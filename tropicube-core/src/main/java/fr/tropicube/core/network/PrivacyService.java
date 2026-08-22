package fr.tropicube.core.network;

import com.google.gson.GsonBuilder;
import fr.tropicube.core.TropicubeCore;
import fr.tropicube.core.managers.DatabaseManager;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Audited data exports and delayed account anonymization with moderation holds. */
public final class PrivacyService {
    public enum Status { PENDING, LEGAL_HOLD, CANCELLED, COMPLETED }
    public record Request(long id, UUID playerId, String type, Status status, long executeAfter) {}
    private static final long DELETE_DELAY = 30L * 24 * 60 * 60 * 1000;
    private static final long EXPORT_RETENTION = 7L * 24 * 60 * 60 * 1000;
    private final TropicubeCore plugin;
    private final DatabaseManager database;
    private final Path exportDirectory;

    public PrivacyService(TropicubeCore plugin, DatabaseManager database) {
        this.plugin = plugin; this.database = database;
        this.exportDirectory = plugin.getDataFolder().toPath().resolve("privacy-exports");
    }

    public CompletableFuture<Long> export(UUID playerId, UUID staffId) {
        return database.supplyAsync(() -> {
            long requestId = createRequest(playerId, staffId, "EXPORT", "PENDING", null);
            try { Files.createDirectories(exportDirectory); }
            catch (java.io.IOException error) { throw new SQLException("Création du dossier d'export impossible", error); }
            Map<String, Object> export = new LinkedHashMap<>();
            export.put("generatedAt", System.currentTimeMillis());
            export.put("playerId", playerId.toString());
            try (Connection connection = database.getConnection()) {
                export.put("account", rows(connection, "SELECT * FROM tropicube_players WHERE uuid = ?", playerId));
                export.put("economy", rows(connection, "SELECT * FROM tropicube_economy WHERE uuid = ?", playerId));
                export.put("transactions", rows(connection, "SELECT * FROM tropicube_transactions WHERE from_uuid = ? OR to_uuid = ?", playerId, playerId));
                export.put("permissions", rows(connection, "SELECT * FROM tropicube_permissions WHERE uuid = ?", playerId));
                export.put("preferences", rows(connection, "SELECT * FROM tropicube_player_preferences WHERE player_uuid = ?", playerId));
                export.put("comfort", rows(connection, "SELECT * FROM tropicube_player_comfort WHERE player_uuid = ?", playerId));
                export.put("progression", rows(connection, "SELECT * FROM tropicube_network_progression WHERE player_uuid = ?", playerId));
                export.put("missions", rows(connection, "SELECT * FROM tropicube_mission_assignments WHERE player_uuid = ?", playerId));
                export.put("notifications", rows(connection, "SELECT * FROM tropicube_notifications WHERE player_uuid = ?", playerId));
                export.put("friendships", rows(connection, "SELECT * FROM tropicube_friendships WHERE player_a = ? OR player_b = ? OR requester_uuid = ?", playerId, playerId, playerId));
                export.put("ignoredPlayers", rows(connection, "SELECT * FROM tropicube_ignored_players WHERE owner_uuid = ? OR ignored_uuid = ?", playerId, playerId));
                export.put("privateMessages", rows(connection, "SELECT * FROM tropicube_private_messages WHERE sender_uuid = ? OR recipient_uuid = ?", playerId, playerId));
                export.put("guildMembership", rows(connection, "SELECT * FROM tropicube_guild_members WHERE player_uuid = ?", playerId));
                export.put("guildOwnership", rows(connection, "SELECT * FROM tropicube_guilds WHERE owner_uuid = ?", playerId));
                export.put("ratings", rows(connection, "SELECT * FROM tropicube_sheepwars_ratings WHERE player_uuid = ?", playerId));
                export.put("mastery", rows(connection, "SELECT * FROM tropicube_sheepwars_kit_mastery WHERE player_uuid = ?", playerId));
                export.put("matches", rows(connection, "SELECT * FROM tropicube_sheepwars_match_players WHERE player_uuid = ?", playerId));
                export.put("seasonArchives", rows(connection, "SELECT * FROM tropicube_sheepwars_season_ratings WHERE player_uuid = ?", playerId));
                export.put("titles", rows(connection, "SELECT * FROM tropicube_profile_titles WHERE player_uuid = ?", playerId));
                export.put("badges", rows(connection, "SELECT * FROM tropicube_profile_badges WHERE player_uuid = ?", playerId));
                export.put("rewardGrants", rows(connection, "SELECT * FROM tropicube_season_reward_grants WHERE player_uuid = ?", playerId));
                export.put("sanctions", rows(connection, "SELECT * FROM tropicube_sanctions WHERE player_uuid = ?", playerId));
                export.put("reports", rows(connection, "SELECT * FROM tropicube_reports WHERE reporter_uuid = ? OR target_uuid = ?", playerId, playerId));
                export.put("reportEvidence", rows(connection, "SELECT e.* FROM tropicube_report_evidence e JOIN tropicube_reports r ON r.id=e.report_id WHERE r.reporter_uuid = ? OR r.target_uuid = ? OR e.author_uuid = ?", playerId, playerId, playerId));
            }
            Path target = exportDirectory.resolve("privacy-export-" + requestId + ".json");
            try { Files.writeString(target, new GsonBuilder().setPrettyPrinting().create().toJson(export), StandardCharsets.UTF_8); }
            catch (java.io.IOException error) { throw new SQLException("Écriture de l'export impossible", error); }
            database.executeUpdate("UPDATE tropicube_privacy_requests SET status='COMPLETED', completed_at=?, result_reference=? WHERE id=?",
                    System.currentTimeMillis(), target.getFileName().toString(), requestId);
            plugin.getLogger().info("event=privacy_export request_id=" + requestId + " staff=" + staffId);
            return requestId;
        });
    }

    public CompletableFuture<Long> requestAnonymization(UUID playerId, UUID staffId) {
        return database.supplyAsync(() -> createRequest(playerId, staffId, "ERASURE", "PENDING",
                System.currentTimeMillis() + DELETE_DELAY));
    }

    public CompletableFuture<Boolean> cancel(long requestId) {
        return database.supplyAsync(() -> database.executeUpdate("""
                UPDATE tropicube_privacy_requests SET status='CANCELLED', completed_at=?
                WHERE id=? AND status IN ('PENDING','LEGAL_HOLD')
                """, System.currentTimeMillis(), requestId) == 1);
    }

    public CompletableFuture<List<Request>> requests(UUID playerId) {
        return database.supplyAsync(() -> {
            List<Request> values = new ArrayList<>();
            try (Connection connection = database.getConnection(); PreparedStatement statement = connection.prepareStatement("""
                    SELECT id, player_uuid, request_type, status, COALESCE(execute_after, 0)
                    FROM tropicube_privacy_requests WHERE player_uuid=? ORDER BY requested_at DESC LIMIT 20
                    """)) {
                statement.setString(1, playerId.toString());
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) values.add(new Request(result.getLong(1), UUID.fromString(result.getString(2)),
                            result.getString(3), Status.valueOf(result.getString(4)), result.getLong(5)));
                }
            }
            return List.copyOf(values);
        });
    }

    public CompletableFuture<Integer> processDue() {
        return database.supplyAsync(this::processDueNow);
    }

    public CompletableFuture<Integer> purgeExports() {
        return CompletableFuture.supplyAsync(() -> {
            if (!Files.isDirectory(exportDirectory)) return 0;
            int removed = 0;
            try (var paths = Files.list(exportDirectory)) {
                for (Path path : paths.toList()) if (Files.getLastModifiedTime(path).toMillis()
                        <= System.currentTimeMillis() - EXPORT_RETENTION) {
                    Files.deleteIfExists(path); removed++;
                }
            } catch (Exception error) { throw new IllegalStateException("Purge des exports impossible", error); }
            return removed;
        });
    }

    private int processDueNow() throws SQLException {
        List<Long> due = new ArrayList<>();
        try (Connection connection = database.getConnection(); PreparedStatement statement = connection.prepareStatement(
                "SELECT id FROM tropicube_privacy_requests WHERE status IN ('PENDING','LEGAL_HOLD') AND request_type='ERASURE' AND execute_after <= ?")) {
            statement.setLong(1, System.currentTimeMillis());
            try (ResultSet result = statement.executeQuery()) { while (result.next()) due.add(result.getLong(1)); }
        }
        int completed = 0;
        for (Long id : due) if (anonymize(id)) completed++;
        return completed;
    }

    private boolean anonymize(long requestId) throws SQLException {
        try (Connection connection = database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                String playerId;
                try (PreparedStatement select = connection.prepareStatement(
                        "SELECT player_uuid FROM tropicube_privacy_requests WHERE id=? AND status IN ('PENDING','LEGAL_HOLD') FOR UPDATE")) {
                    select.setLong(1, requestId);
                    try (ResultSet result = select.executeQuery()) {
                        if (!result.next()) { connection.rollback(); return false; }
                        playerId = result.getString(1);
                    }
                }
                if (hasLegalHold(connection, playerId)) {
                    updateRequest(connection, requestId, "LEGAL_HOLD"); connection.commit(); return false;
                }
                String anonymous = "anon-" + sha256(playerId).substring(0, 31);
                transferOwnedGuilds(connection, playerId);
                execute(connection, "DELETE FROM tropicube_private_messages WHERE sender_uuid=? OR recipient_uuid=?", playerId, playerId);
                execute(connection, "DELETE FROM tropicube_sheepwars_match_players WHERE player_uuid=?", playerId);
                execute(connection, "DELETE FROM tropicube_sanctions WHERE player_uuid=?", playerId);
                execute(connection, "DELETE FROM tropicube_sheepwars WHERE uuid=?", playerId);
                execute(connection, "UPDATE tropicube_transactions SET from_uuid=? WHERE from_uuid=?", anonymous, playerId);
                execute(connection, "UPDATE tropicube_transactions SET to_uuid=? WHERE to_uuid=?", anonymous, playerId);
                execute(connection, "UPDATE tropicube_guild_audit SET actor_uuid=? WHERE actor_uuid=?", anonymous, playerId);
                execute(connection, "UPDATE tropicube_guild_invites SET invited_by=? WHERE invited_by=?", anonymous, playerId);
                execute(connection, "UPDATE tropicube_reports SET reporter_uuid=? WHERE reporter_uuid=?", anonymous, playerId);
                execute(connection, "UPDATE tropicube_reports SET target_uuid=? WHERE target_uuid=?", anonymous, playerId);
                execute(connection, "UPDATE tropicube_report_evidence SET author_uuid=? WHERE author_uuid=?", anonymous, playerId);
                execute(connection, "DELETE FROM tropicube_players WHERE uuid=?", playerId);
                updateRequest(connection, requestId, "COMPLETED");
                connection.commit();
                plugin.getLogger().info("event=privacy_anonymized request_id=" + requestId);
                return true;
            } catch (Exception error) { connection.rollback(); throw error; }
            finally { connection.setAutoCommit(true); }
        }
    }

    private boolean hasLegalHold(Connection connection, String playerId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT EXISTS(SELECT 1 FROM tropicube_sanctions WHERE player_uuid=? AND active=TRUE)
                    OR EXISTS(SELECT 1 FROM tropicube_reports WHERE (reporter_uuid=? OR target_uuid=?)
                        AND (status IN ('OPEN','CLAIMED') OR evidence_expires_at > ?))
                """)) {
            statement.setString(1, playerId); statement.setString(2, playerId); statement.setString(3, playerId);
            statement.setLong(4, System.currentTimeMillis());
            try (ResultSet result = statement.executeQuery()) { result.next(); return result.getBoolean(1); }
        }
    }

    private void transferOwnedGuilds(Connection connection, String ownerId) throws SQLException {
        List<Long> guildIds = new ArrayList<>();
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT id FROM tropicube_guilds WHERE owner_uuid=? FOR UPDATE")) {
            select.setString(1, ownerId);
            try (ResultSet result = select.executeQuery()) {
                while (result.next()) guildIds.add(result.getLong(1));
            }
        }
        long activeSince = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000;
        for (long guildId : guildIds) {
            String successor = null;
            try (PreparedStatement select = connection.prepareStatement("""
                    SELECT player_uuid FROM tropicube_guild_members
                    WHERE guild_id=? AND player_uuid<>?
                    ORDER BY (last_active_at >= ?) DESC, joined_at ASC LIMIT 1 FOR UPDATE
                    """)) {
                select.setLong(1, guildId); select.setString(2, ownerId); select.setLong(3, activeSince);
                try (ResultSet result = select.executeQuery()) { if (result.next()) successor = result.getString(1); }
            }
            if (successor == null) {
                try (PreparedStatement delete = connection.prepareStatement("DELETE FROM tropicube_guilds WHERE id=?")) {
                    delete.setLong(1, guildId); delete.executeUpdate();
                }
                continue;
            }
            try (PreparedStatement guild = connection.prepareStatement(
                    "UPDATE tropicube_guilds SET owner_uuid=?, updated_at=? WHERE id=?")) {
                guild.setString(1, successor); guild.setLong(2, System.currentTimeMillis());
                guild.setLong(3, guildId); guild.executeUpdate();
            }
            try (PreparedStatement member = connection.prepareStatement(
                    "UPDATE tropicube_guild_members SET role='OWNER' WHERE guild_id=? AND player_uuid=?")) {
                member.setLong(1, guildId); member.setString(2, successor); member.executeUpdate();
            }
        }
    }

    private long createRequest(UUID playerId, UUID staffId, String type, String status, Long executeAfter) throws SQLException {
        try (Connection connection = database.getConnection(); PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO tropicube_privacy_requests(player_uuid, requested_by, request_type, status, requested_at, execute_after)
                VALUES (?, ?, ?, ?, ?, ?)
                """, java.sql.Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, playerId.toString()); statement.setString(2, staffId.toString());
            statement.setString(3, type); statement.setString(4, status); statement.setLong(5, System.currentTimeMillis());
            if (executeAfter == null) statement.setNull(6, java.sql.Types.BIGINT); else statement.setLong(6, executeAfter);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) { if (!keys.next()) throw new SQLException("ID de demande absent"); return keys.getLong(1); }
        }
    }

    private List<Map<String, Object>> rows(Connection connection, String sql, UUID... ids) throws SQLException {
        List<Map<String, Object>> values = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < ids.length; index++) statement.setString(index + 1, ids[index].toString());
            try (ResultSet result = statement.executeQuery()) {
                ResultSetMetaData metadata = result.getMetaData();
                while (result.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int index = 1; index <= metadata.getColumnCount(); index++) row.put(metadata.getColumnLabel(index), result.getObject(index));
                    values.add(row);
                }
            }
        }
        return List.copyOf(values);
    }

    private void updateRequest(Connection connection, long id, String status) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE tropicube_privacy_requests SET status=?, completed_at=CASE WHEN ?='COMPLETED' THEN ? ELSE completed_at END WHERE id=?")) {
            statement.setString(1, status); statement.setString(2, status);
            statement.setLong(3, System.currentTimeMillis()); statement.setLong(4, id); statement.executeUpdate();
        }
    }
    private void execute(Connection connection, String sql, String... values) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < values.length; index++) statement.setString(index + 1, values[index]);
            statement.executeUpdate();
        }
    }
    private static String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
