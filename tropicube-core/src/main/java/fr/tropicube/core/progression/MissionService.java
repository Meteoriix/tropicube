package fr.tropicube.core.progression;

import fr.tropicube.core.TropicubeCore;
import fr.tropicube.core.managers.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Personal daily/weekly rotations, bounded rerolls, progress and rewards. */
public final class MissionService {
    public enum Rotation { DAILY, WEEKLY }
    public enum RerollResult { REROLLED, LIMIT_REACHED, INVALID_SLOT, NO_REPLACEMENT }
    public enum ClaimResult { CLAIMED, INCOMPLETE, ALREADY_CLAIMED, INVALID_SLOT }
    public record Assignment(Rotation rotation, String rotationKey, int slot, MissionCatalog.Mission mission,
                             long progress, boolean completed, boolean rewarded, int rerolls) {}

    private static final ZoneId ROTATION_ZONE = ZoneId.of("Europe/Paris");
    private final TropicubeCore plugin;
    private final DatabaseManager database;
    private final MissionCatalog catalog;

    public MissionService(TropicubeCore plugin, DatabaseManager database, MissionCatalog catalog) {
        this.plugin = plugin;
        this.database = database;
        this.catalog = catalog;
    }

    public CompletableFuture<List<Assignment>> current(UUID playerId) {
        return database.supplyAsync(() -> {
            ensureRotation(playerId, Rotation.DAILY, dailyKey(), 5, catalog.daily());
            ensureRotation(playerId, Rotation.WEEKLY, weeklyKey(), 3, catalog.weekly());
            return loadCurrent(playerId);
        });
    }

    public CompletableFuture<RerollResult> rerollDaily(UUID playerId, int slot, int allowance) {
        return database.supplyAsync(() -> reroll(playerId, slot, Math.max(0, allowance)));
    }

    public CompletableFuture<Void> progress(UUID playerId, String event, long amount) {
        if (amount <= 0) return CompletableFuture.completedFuture(null);
        return current(playerId).thenCompose(assignments -> database.supplyAsync(() -> {
            for (Assignment assignment : assignments) {
                if (!assignment.mission().event().equals(event) || assignment.rewarded()) continue;
                database.executeUpdate("""
                        UPDATE tropicube_mission_assignments
                        SET progress = LEAST(target, progress + ?),
                            completed_at = CASE WHEN progress + ? >= target THEN COALESCE(completed_at, ?) ELSE completed_at END
                        WHERE player_uuid = ? AND rotation_type = ? AND rotation_key = ? AND slot_index = ?
                        """, amount, amount, System.currentTimeMillis(), playerId.toString(),
                        assignment.rotation().name(), assignment.rotationKey(), assignment.slot());
            }
            return null;
        }));
    }

    public CompletableFuture<ClaimResult> claim(UUID playerId, Rotation rotation, int slot) {
        return current(playerId).thenCompose(ignored -> database.supplyAsync(() -> claimNow(playerId, rotation, slot)));
    }

    private ClaimResult claimNow(UUID playerId, Rotation rotation, int slot) throws SQLException {
        String key = rotation == Rotation.DAILY ? dailyKey() : weeklyKey();
        Assignment assignment = load(playerId, rotation, key, slot);
        if (assignment == null) return ClaimResult.INVALID_SLOT;
        if (assignment.rewarded()) return ClaimResult.ALREADY_CLAIMED;
        if (!assignment.completed()) return ClaimResult.INCOMPLETE;
        try (Connection connection = database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                int changed;
                try (PreparedStatement reward = connection.prepareStatement("""
                        UPDATE tropicube_mission_assignments SET rewarded_at = ?
                        WHERE player_uuid = ? AND rotation_type = ? AND rotation_key = ? AND slot_index = ?
                          AND completed_at IS NOT NULL AND rewarded_at IS NULL
                        """)) {
                    reward.setLong(1, System.currentTimeMillis());
                    reward.setString(2, playerId.toString());
                    reward.setString(3, rotation.name());
                    reward.setString(4, key);
                    reward.setInt(5, slot);
                    changed = reward.executeUpdate();
                }
                if (changed != 1) { connection.rollback(); return ClaimResult.ALREADY_CLAIMED; }
                long now = System.currentTimeMillis();
                try (PreparedStatement economy = connection.prepareStatement("""
                        INSERT INTO tropicube_economy(uuid, balance, total_earned, total_spent, last_updated)
                        VALUES (?, ?, ?, 0, ?)
                        ON DUPLICATE KEY UPDATE balance = balance + VALUES(balance),
                            total_earned = total_earned + VALUES(total_earned), last_updated = VALUES(last_updated)
                        """)) {
                    economy.setString(1, playerId.toString());
                    economy.setDouble(2, assignment.mission().currency());
                    economy.setDouble(3, assignment.mission().currency());
                    economy.setLong(4, now);
                    economy.executeUpdate();
                }
                try (PreparedStatement transaction = connection.prepareStatement("""
                        INSERT INTO tropicube_transactions(from_uuid, to_uuid, amount, reason, transaction_type, timestamp)
                        VALUES (NULL, ?, ?, ?, 'REWARD', ?)
                        """)) {
                    transaction.setString(1, playerId.toString());
                    transaction.setDouble(2, assignment.mission().currency());
                    transaction.setString(3, "Mission " + assignment.mission().id());
                    transaction.setLong(4, now);
                    transaction.executeUpdate();
                }
                long currentExperience = 0;
                try (PreparedStatement select = connection.prepareStatement(
                        "SELECT experience FROM tropicube_network_progression WHERE player_uuid = ? FOR UPDATE")) {
                    select.setString(1, playerId.toString());
                    try (ResultSet result = select.executeQuery()) {
                        if (result.next()) currentExperience = result.getLong(1);
                    }
                }
                long updatedExperience = Math.addExact(currentExperience, assignment.mission().experience());
                try (PreparedStatement progression = connection.prepareStatement("""
                        INSERT INTO tropicube_network_progression(player_uuid, experience, level, updated_at)
                        VALUES (?, ?, ?, ?)
                        ON DUPLICATE KEY UPDATE experience=VALUES(experience), level=VALUES(level), updated_at=VALUES(updated_at)
                        """)) {
                    progression.setString(1, playerId.toString());
                    progression.setLong(2, updatedExperience);
                    progression.setInt(3, NetworkProgressionService.levelForExperience(updatedExperience));
                    progression.setLong(4, now);
                    progression.executeUpdate();
                }
                if (assignment.mission().rerollTokens() > 0) {
                    try (PreparedStatement comfort = connection.prepareStatement("""
                            INSERT INTO tropicube_player_comfort(player_uuid, selected_title_id, reroll_tokens, updated_at)
                            VALUES (?, NULL, ?, ?) ON DUPLICATE KEY UPDATE
                            reroll_tokens=LEAST(5, reroll_tokens + VALUES(reroll_tokens)), updated_at=VALUES(updated_at)
                            """)) {
                        comfort.setString(1, playerId.toString());
                        comfort.setInt(2, assignment.mission().rerollTokens());
                        comfort.setLong(3, now);
                        comfort.executeUpdate();
                    }
                }
                connection.commit();
                plugin.getEconomyManager().invalidateCache(playerId);
                return ClaimResult.CLAIMED;
            } catch (SQLException | RuntimeException error) {
                connection.rollback();
                throw error;
            } finally { connection.setAutoCommit(true); }
        }
    }

    private RerollResult reroll(UUID playerId, int slot, int allowance) throws SQLException {
        String key = dailyKey();
        ensureRotation(playerId, Rotation.DAILY, key, 5, catalog.daily());
        List<Assignment> daily = load(playerId, Rotation.DAILY, key);
        Assignment current = daily.stream().filter(value -> value.slot() == slot).findFirst().orElse(null);
        if (current == null || current.rewarded()) return RerollResult.INVALID_SLOT;
        int used = daily.stream().mapToInt(Assignment::rerolls).sum();
        Set<String> assigned = new HashSet<>();
        daily.forEach(value -> assigned.add(value.mission().id()));
        List<MissionCatalog.Mission> candidates = catalog.daily().stream()
                .filter(mission -> !assigned.contains(mission.id())).toList();
        if (candidates.isEmpty()) return RerollResult.NO_REPLACEMENT;
        MissionCatalog.Mission replacement = candidates.get(Math.floorMod(
                java.util.Objects.hash(playerId, key, slot, used, catalog.version()), candidates.size()));
        try (Connection connection = database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                if (used >= allowance) {
                    try (PreparedStatement token = connection.prepareStatement("""
                            UPDATE tropicube_player_comfort SET reroll_tokens = reroll_tokens - 1, updated_at = ?
                            WHERE player_uuid = ? AND reroll_tokens > 0
                            """)) {
                        token.setLong(1, System.currentTimeMillis());
                        token.setString(2, playerId.toString());
                        if (token.executeUpdate() != 1) {
                            connection.rollback();
                            return RerollResult.LIMIT_REACHED;
                        }
                    }
                }
                try (PreparedStatement update = connection.prepareStatement("""
                        UPDATE tropicube_mission_assignments
                        SET mission_id = ?, progress = 0, target = ?, completed_at = NULL,
                            rewarded_at = NULL, reroll_count = reroll_count + 1
                        WHERE player_uuid = ? AND rotation_type = 'DAILY' AND rotation_key = ? AND slot_index = ?
                        """)) {
                    update.setString(1, replacement.id());
                    update.setLong(2, replacement.target());
                    update.setString(3, playerId.toString());
                    update.setString(4, key);
                    update.setInt(5, slot);
                    if (update.executeUpdate() != 1) {
                        connection.rollback();
                        return RerollResult.INVALID_SLOT;
                    }
                }
                connection.commit();
                return RerollResult.REROLLED;
            } catch (SQLException | RuntimeException error) {
                connection.rollback();
                throw error;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    private void ensureRotation(UUID playerId, Rotation rotation, String key, int slots,
                                List<MissionCatalog.Mission> definitions) throws SQLException {
        List<Assignment> existing = load(playerId, rotation, key);
        if (existing.size() >= slots) return;
        List<MissionCatalog.Mission> shuffled = new ArrayList<>(definitions);
        Collections.shuffle(shuffled, new Random(java.util.Objects.hash(playerId, key, catalog.version())));
        Set<Integer> occupied = new HashSet<>();
        Set<String> assigned = new HashSet<>();
        existing.forEach(value -> { occupied.add(value.slot()); assigned.add(value.mission().id()); });
        int definition = 0;
        for (int slot = 0; slot < slots; slot++) {
            if (occupied.contains(slot)) continue;
            while (assigned.contains(shuffled.get(definition).id())) definition++;
            MissionCatalog.Mission mission = shuffled.get(definition++);
            database.executeUpdate("""
                    INSERT IGNORE INTO tropicube_mission_assignments
                        (player_uuid, rotation_type, rotation_key, slot_index, mission_id, progress, target, reroll_count)
                    VALUES (?, ?, ?, ?, ?, 0, ?, 0)
                    """, playerId.toString(), rotation.name(), key, slot, mission.id(), mission.target());
            assigned.add(mission.id());
        }
    }

    private List<Assignment> loadCurrent(UUID playerId) throws SQLException {
        List<Assignment> values = new ArrayList<>(load(playerId, Rotation.DAILY, dailyKey()));
        values.addAll(load(playerId, Rotation.WEEKLY, weeklyKey()));
        return List.copyOf(values);
    }

    private Assignment load(UUID playerId, Rotation rotation, String key, int slot) throws SQLException {
        return load(playerId, rotation, key).stream().filter(value -> value.slot() == slot).findFirst().orElse(null);
    }

    private List<Assignment> load(UUID playerId, Rotation rotation, String key) throws SQLException {
        List<Assignment> values = new ArrayList<>();
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT slot_index, mission_id, progress, target, completed_at, rewarded_at, reroll_count
                     FROM tropicube_mission_assignments
                     WHERE player_uuid = ? AND rotation_type = ? AND rotation_key = ? ORDER BY slot_index
                     """)) {
            statement.setString(1, playerId.toString());
            statement.setString(2, rotation.name());
            statement.setString(3, key);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) values.add(new Assignment(rotation, key, result.getInt("slot_index"),
                        catalog.find(result.getString("mission_id")), result.getLong("progress"),
                        result.getObject("completed_at") != null, result.getObject("rewarded_at") != null,
                        result.getInt("reroll_count")));
            }
        }
        return List.copyOf(values);
    }

    static String dailyKey() { return LocalDate.now(ROTATION_ZONE).toString(); }
    static String weeklyKey() {
        LocalDate date = LocalDate.now(ROTATION_ZONE);
        WeekFields fields = WeekFields.ISO;
        return "%d-W%02d".formatted(date.get(fields.weekBasedYear()), date.get(fields.weekOfWeekBasedYear()));
    }
}
