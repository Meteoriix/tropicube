package fr.tropicube.sheepwars.competitive;

import fr.tropicube.core.TropicubeCore;
import fr.tropicube.core.managers.DatabaseManager;
import fr.tropicube.sheepwars.player.PlayerKit;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/** Persists matches, shared rating, kit mastery, summaries and abandonment penalties off-thread. */
public final class SheepWarsProgressionService {
    public enum BranchSelection { SELECTED, LOCKED }
    public enum SummaryVisibility { PUBLIC, TEAM, PRIVATE }
    public record Participant(UUID playerId, String team, PlayerKit kit, int kills,
                              int sheepLaunched, boolean survived) {}
    public record RatingView(CompetitiveRating rating, long seasonId) {}
    public record MasteryView(long experience, int level, KitMasteryBranch branch, int catalogVersion) {}
    public record Penalty(int recentStrikes, long until) {
        public boolean active(long now) { return until > now; }
    }
    public record Completion(UUID matchId, long seasonId, Map<UUID, Double> ratingDeltas) {
        public Completion { ratingDeltas = Map.copyOf(ratingDeltas); }
    }
    private record PenaltyState(int recentStrikes, long lastAbandonAt, long until) {}

    private static final ZoneId SEASON_ZONE = ZoneId.of("Europe/Paris");
    private static final long STRIKE_WINDOW = 7L * 24 * 60 * 60 * 1000;
    private static final long[] PENALTIES = {60_000, 300_000, 900_000, 3_600_000};

    private final DatabaseManager database;
    private final TropicubeCore core;
    private final KitMasteryCatalog masteryCatalog;
    private final SeasonRewardCatalog seasonRewards;
    private final ConcurrentHashMap<UUID, SummaryVisibility> summaryVisibility = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Map<PlayerKit, MasteryView>> masteries = new ConcurrentHashMap<>();

    public SheepWarsProgressionService(DatabaseManager database, TropicubeCore core,
                                       KitMasteryCatalog masteryCatalog,
                                       SeasonRewardCatalog seasonRewards) {
        this.database = database;
        this.core = core;
        this.masteryCatalog = masteryCatalog;
        this.seasonRewards = seasonRewards;
    }

    public CompletableFuture<Completion> complete(String instanceId, SheepWarsMode mode, String mapId,
                                                   String winningTeam, long startedAt,
                                                   List<Participant> participants) {
        List<Participant> snapshot = List.copyOf(participants);
        return database.supplyAsync(() -> persistMatch(instanceId, mode, mapId, winningTeam, startedAt, snapshot))
                .thenApply(completion -> {
                    rewardPlayers(mode, winningTeam, completion, snapshot);
                    return completion;
                });
    }

    public CompletableFuture<RatingView> rating(UUID playerId) {
        return database.supplyAsync(() -> {
            try (Connection connection = database.getConnection()) {
                long seasonId = currentSeason(connection);
                return new RatingView(loadRating(connection, playerId, seasonId), seasonId);
            }
        });
    }

    public CompletableFuture<BranchSelection> selectBranch(UUID playerId, PlayerKit kit, KitMasteryBranch branch) {
        if (kit == PlayerKit.NONE) throw new IllegalArgumentException("Un kit est requis");
        return database.supplyAsync(() -> {
            MasteryView current = loadMastery(playerId, kit);
            if (current.level() < masteryCatalog.unlockLevel()) return BranchSelection.LOCKED;
            database.executeUpdate("""
                    INSERT INTO tropicube_sheepwars_kit_mastery
                        (player_uuid, kit_id, experience, level, selected_branch, selected_catalog_version, updated_at)
                    VALUES (?, ?, 0, 1, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE selected_branch=VALUES(selected_branch),
                        selected_catalog_version=VALUES(selected_catalog_version), updated_at=VALUES(updated_at)
                    """, playerId.toString(), kit.name(), branch.name(), masteryCatalog.version(), System.currentTimeMillis());
            MasteryView updated = new MasteryView(current.experience(), current.level(), branch, masteryCatalog.version());
            masteries.computeIfAbsent(playerId, ignored -> new ConcurrentHashMap<>()).put(kit, updated);
            return BranchSelection.SELECTED;
        });
    }

    public CompletableFuture<Map<PlayerKit, MasteryView>> loadMasteries(UUID playerId) {
        return database.supplyAsync(() -> {
            Map<PlayerKit, MasteryView> values = new ConcurrentHashMap<>();
            try (Connection connection = database.getConnection(); PreparedStatement select = connection.prepareStatement("""
                    SELECT kit_id, experience, level, selected_branch, selected_catalog_version
                    FROM tropicube_sheepwars_kit_mastery WHERE player_uuid=?
                    """)) {
                select.setString(1, playerId.toString());
                try (ResultSet result = select.executeQuery()) {
                    while (result.next()) {
                        try {
                            PlayerKit kit = PlayerKit.valueOf(result.getString(1));
                            String raw = result.getString(4);
                            KitMasteryBranch branch = raw == null ? null : KitMasteryBranch.valueOf(raw);
                            values.put(kit, new MasteryView(result.getLong(2), result.getInt(3), branch, result.getInt(5)));
                        } catch (IllegalArgumentException ignored) { }
                    }
                }
            }
            masteries.put(playerId, values);
            return Map.copyOf(values);
        });
    }

    public MasteryView mastery(UUID playerId, PlayerKit kit) {
        return masteries.getOrDefault(playerId, Map.of()).getOrDefault(kit,
                new MasteryView(0, 1, null, masteryCatalog.version()));
    }

    public double masteryEffect(UUID playerId, PlayerKit kit, String effect, double fallback) {
        MasteryView view = mastery(playerId, kit);
        if (view.branch() == null) return fallback;
        return masteryCatalog.branch(kit, view.branch()).effect(effect, fallback);
    }

    private MasteryView loadMastery(UUID playerId, PlayerKit kit) throws SQLException {
        try (Connection connection = database.getConnection(); PreparedStatement select = connection.prepareStatement("""
                SELECT experience, level, selected_branch, selected_catalog_version
                FROM tropicube_sheepwars_kit_mastery WHERE player_uuid=? AND kit_id=?
                """)) {
            select.setString(1, playerId.toString()); select.setString(2, kit.name());
            try (ResultSet result = select.executeQuery()) {
                if (!result.next()) return new MasteryView(0, 1, null, masteryCatalog.version());
                String raw = result.getString(3);
                return new MasteryView(result.getLong(1), result.getInt(2),
                        raw == null ? null : KitMasteryBranch.valueOf(raw), result.getInt(4));
            }
        }
    }

    public CompletableFuture<Void> setSummaryVisibility(UUID playerId, SummaryVisibility visibility) {
        return database.supplyAsync(() -> {
            database.executeUpdate("""
                    INSERT INTO tropicube_sheepwars_preferences(player_uuid, summary_visibility, updated_at)
                    VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE
                    summary_visibility=VALUES(summary_visibility), updated_at=VALUES(updated_at)
                    """, playerId.toString(), visibility.name(), System.currentTimeMillis());
            summaryVisibility.put(playerId, visibility);
            return null;
        });
    }

    public CompletableFuture<SummaryVisibility> loadSummaryVisibility(UUID playerId) {
        return database.supplyAsync(() -> {
            try (Connection connection = database.getConnection(); PreparedStatement select = connection.prepareStatement(
                    "SELECT summary_visibility FROM tropicube_sheepwars_preferences WHERE player_uuid = ?")) {
                select.setString(1, playerId.toString());
                try (ResultSet result = select.executeQuery()) {
                    SummaryVisibility value = result.next()
                            ? parseVisibility(result.getString(1)) : SummaryVisibility.PUBLIC;
                    summaryVisibility.put(playerId, value);
                    return value;
                }
            }
        });
    }

    /** Applies the subject's preference to a viewer inside the current match. */
    public boolean canPublishDetails(UUID subject, UUID viewer, boolean sameTeam) {
        if (subject.equals(viewer)) return true;
        return switch (summaryVisibility.getOrDefault(subject, SummaryVisibility.PUBLIC)) {
            case PUBLIC -> true;
            case TEAM -> sameTeam;
            case PRIVATE -> false;
        };
    }

    public CompletableFuture<Penalty> recordAbandon(UUID playerId) {
        return database.supplyAsync(() -> recordAbandonNow(playerId));
    }

    public CompletableFuture<Penalty> penalty(UUID playerId) {
        return database.supplyAsync(() -> loadPenalty(playerId));
    }

    private Completion persistMatch(String instanceId, SheepWarsMode mode, String mapId, String winningTeam,
                                    long startedAt, List<Participant> participants) throws SQLException {
        UUID matchId = UUID.randomUUID();
        try (Connection connection = database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                long seasonId = currentSeason(connection);
                try (PreparedStatement match = connection.prepareStatement("""
                        INSERT INTO tropicube_sheepwars_matches
                            (id, instance_id, season_id, mode, map_id, winning_team, started_at, ended_at,
                             timeline_json, mastery_catalog_version)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, '[]', ?)
                        """)) {
                    match.setString(1, matchId.toString());
                    match.setString(2, instanceId == null || instanceId.isBlank() ? "local-" + matchId : instanceId);
                    match.setLong(3, seasonId); match.setString(4, mode.name()); match.setString(5, mapId);
                    match.setString(6, winningTeam); match.setLong(7, startedAt);
                    match.setLong(8, System.currentTimeMillis()); match.setInt(9, masteryCatalog.version());
                    match.executeUpdate();
                }
                double redAverage = averageRating(connection, participants, "RED", seasonId);
                double blueAverage = averageRating(connection, participants, "BLUE", seasonId);
                java.util.Map<UUID, Double> ratingDeltas = new java.util.HashMap<>();
                for (Participant participant : participants) {
                    CompetitiveRating before = loadRating(connection, participant.playerId(), seasonId);
                    CompetitiveRating after = before;
                    if (mode.ranked()) {
                        double score = winningTeam == null ? 0.5 : winningTeam.equals(participant.team()) ? 1 : 0;
                        double opponent = participant.team().equals("RED") ? blueAverage : redAverage;
                        after = RatingCalculator.update(before, opponent, score).rating();
                        ratingDeltas.put(participant.playerId(), after.value() - before.value());
                        saveRating(connection, participant.playerId(), seasonId, after);
                    }
                    insertParticipant(connection, matchId, participant, mode.ranked() ? before.value() : null,
                            mode.ranked() ? after.value() : null,
                            mode == SheepWarsMode.CUSTOM ? 0
                                    : participant.team().equals(winningTeam) ? 150 : 100);
                    if (mode.kitMasteryEnabled() && participant.kit() != PlayerKit.NONE) {
                        addMastery(connection, participant, winningTeam);
                    }
                }
                connection.commit();
                return new Completion(matchId, seasonId, ratingDeltas);
            } catch (SQLException | RuntimeException error) {
                connection.rollback();
                throw error;
            } finally { connection.setAutoCommit(true); }
        }
    }

    private void rewardPlayers(SheepWarsMode mode, String winner, Completion completion, List<Participant> participants) {
        if (mode == SheepWarsMode.CUSTOM) return;
        for (Participant participant : participants) {
            boolean won = winner != null && winner.equals(participant.team());
            core.getNetworkProgressionService().addExperience(participant.playerId(), won ? 150 : 100);
            core.getMissionService().progress(participant.playerId(), "MATCH_PLAYED", 1);
            if (won) core.getMissionService().progress(participant.playerId(), "MATCH_WON", 1);
            if (participant.survived()) core.getMissionService().progress(participant.playerId(), "MATCH_SURVIVED", 1);
            if (participant.kills() > 0) core.getMissionService().progress(participant.playerId(), "PLAYER_KILL", participant.kills());
            if (participant.sheepLaunched() > 0) core.getMissionService().progress(participant.playerId(), "SHEEP_LAUNCHED", participant.sheepLaunched());
            if (mode.ranked()) {
                core.getGuildService().recordRankedResult(participant.playerId(), completion.seasonId(),
                        completion.ratingDeltas().getOrDefault(participant.playerId(), 0.0));
            }
        }
    }

    private long currentSeason(Connection connection) throws SQLException {
        LocalDate today = LocalDate.now(SEASON_ZONE);
        int quarter = (today.getMonthValue() - 1) / 3 + 1;
        LocalDate start = LocalDate.of(today.getYear(), (quarter - 1) * 3 + 1, 1);
        LocalDate end = start.plusMonths(3);
        String key = "SW-" + today.getYear() + "-Q" + quarter;
        archivePreviousSeasons(connection, key);
        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO tropicube_seasons(season_key, display_name, starts_at, ends_at, status)
                VALUES (?, ?, ?, ?, 'ACTIVE') ON DUPLICATE KEY UPDATE status='ACTIVE'
                """)) {
            insert.setString(1, key); insert.setString(2, "SheepWars " + today.getYear() + " Q" + quarter);
            insert.setLong(3, start.atStartOfDay(SEASON_ZONE).toInstant().toEpochMilli());
            insert.setLong(4, end.atStartOfDay(SEASON_ZONE).toInstant().toEpochMilli()); insert.executeUpdate();
        }
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT id FROM tropicube_seasons WHERE season_key = ?")) {
            select.setString(1, key);
            try (ResultSet result = select.executeQuery()) {
                if (!result.next()) throw new SQLException("Saison SheepWars introuvable après création");
                return result.getLong(1);
            }
        }
    }

    private void archivePreviousSeasons(Connection connection, String currentKey) throws SQLException {
        List<Season> previous = new java.util.ArrayList<>();
        try (PreparedStatement select = connection.prepareStatement("""
                SELECT id, season_key FROM tropicube_seasons
                WHERE season_key LIKE 'SW-%' AND season_key <> ? AND status='ACTIVE' FOR UPDATE
                """)) {
            select.setString(1, currentKey);
            try (ResultSet result = select.executeQuery()) {
                while (result.next()) previous.add(new Season(result.getLong(1), result.getString(2)));
            }
        }
        for (Season season : previous) {
            archiveRatings(connection, season);
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE tropicube_seasons SET status='ARCHIVED' WHERE id=?")) {
                update.setLong(1, season.id()); update.executeUpdate();
            }
        }
    }

    private void archiveRatings(Connection connection, Season season) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement("""
                SELECT r.player_uuid, r.rating, r.uncertainty, r.placements_remaining,
                       COUNT(DISTINCT mp.match_id) AS ranked_matches
                FROM tropicube_sheepwars_ratings r
                LEFT JOIN tropicube_sheepwars_matches m ON m.season_id=r.season_id AND m.mode LIKE 'RANKED_%'
                LEFT JOIN tropicube_sheepwars_match_players mp ON mp.match_id=m.id AND mp.player_uuid=r.player_uuid
                WHERE r.season_id=? GROUP BY r.player_uuid, r.rating, r.uncertainty, r.placements_remaining
                """)) {
            select.setLong(1, season.id());
            try (ResultSet result = select.executeQuery()) {
                while (result.next()) {
                    String playerId = result.getString(1);
                    double rating = result.getDouble(2), uncertainty = result.getDouble(3);
                    int placements = result.getInt(4), matches = result.getInt(5);
                    RankTier tier = RankTier.forRating(rating);
                    try (PreparedStatement archive = connection.prepareStatement("""
                            INSERT IGNORE INTO tropicube_sheepwars_season_ratings
                                (season_id, player_uuid, final_rating, final_uncertainty, final_tier,
                                 ranked_matches, eligible, archived_at)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                            """)) {
                        archive.setLong(1, season.id()); archive.setString(2, playerId);
                        archive.setDouble(3, rating); archive.setDouble(4, uncertainty);
                        archive.setString(5, tier.name()); archive.setInt(6, matches);
                        archive.setBoolean(7, placements == 0); archive.setLong(8, System.currentTimeMillis());
                        archive.executeUpdate();
                    }
                    if (placements == 0) grantSeasonReward(connection, season, playerId, tier);
                }
            }
        }
    }

    private void grantSeasonReward(Connection connection, Season season, String playerId,
                                   RankTier tier) throws SQLException {
        SeasonRewardCatalog.Reward reward = seasonRewards.reward(tier);
        int inserted;
        try (PreparedStatement grant = connection.prepareStatement("""
                INSERT IGNORE INTO tropicube_season_reward_grants
                    (season_id, player_uuid, tier, currency_amount, granted_at)
                VALUES (?, ?, ?, ?, ?)
                """)) {
            grant.setLong(1, season.id()); grant.setString(2, playerId); grant.setString(3, tier.name());
            grant.setDouble(4, reward.currency()); grant.setLong(5, System.currentTimeMillis());
            inserted = grant.executeUpdate();
        }
        if (inserted != 1) return;
        long now = System.currentTimeMillis();
        try (PreparedStatement economy = connection.prepareStatement("""
                INSERT INTO tropicube_economy(uuid, balance, total_earned, total_spent, last_updated)
                VALUES (?, ?, ?, 0, ?) ON DUPLICATE KEY UPDATE balance=balance+VALUES(balance),
                    total_earned=total_earned+VALUES(total_earned), last_updated=VALUES(last_updated)
                """)) {
            economy.setString(1, playerId); economy.setDouble(2, reward.currency());
            economy.setDouble(3, reward.currency()); economy.setLong(4, now); economy.executeUpdate();
        }
        try (PreparedStatement transaction = connection.prepareStatement("""
                INSERT INTO tropicube_transactions(from_uuid, to_uuid, amount, reason, transaction_type, timestamp)
                VALUES (NULL, ?, ?, ?, 'REWARD', ?)
                """)) {
            transaction.setString(1, playerId); transaction.setDouble(2, reward.currency());
            transaction.setString(3, "SheepWars season " + season.key() + " " + tier.name());
            transaction.setLong(4, now); transaction.executeUpdate();
        }
        String suffix = season.key().toLowerCase(java.util.Locale.ROOT) + "-" + tier.name().toLowerCase(java.util.Locale.ROOT);
        if (reward.title()) insertProfileReward(connection, "tropicube_profile_titles", "title_id",
                playerId, "sw-season-" + suffix, "season.reward-title-" + tier.name().toLowerCase(java.util.Locale.ROOT),
                season.id(), now);
        if (reward.badge()) insertProfileReward(connection, "tropicube_profile_badges", "badge_id",
                playerId, "sw-season-" + suffix, "season.reward-badge-" + tier.name().toLowerCase(java.util.Locale.ROOT),
                season.id(), now);
    }

    private void insertProfileReward(Connection connection, String table, String idColumn, String playerId,
                                     String rewardId, String displayKey, long seasonId, long now) throws SQLException {
        String sql = "INSERT IGNORE INTO " + table + "(player_uuid," + idColumn
                + ",display_key,unlocked_at,season_id) VALUES (?,?,?,?,?)";
        try (PreparedStatement insert = connection.prepareStatement(sql)) {
            insert.setString(1, playerId); insert.setString(2, rewardId); insert.setString(3, displayKey);
            insert.setLong(4, now); insert.setLong(5, seasonId); insert.executeUpdate();
        }
    }

    private record Season(long id, String key) { }

    private CompetitiveRating loadRating(Connection connection, UUID playerId, long seasonId) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement("""
                SELECT rating, uncertainty, placements_remaining, season_id
                FROM tropicube_sheepwars_ratings WHERE player_uuid = ?
                """)) {
            select.setString(1, playerId.toString());
            try (ResultSet result = select.executeQuery()) {
                if (!result.next()) return CompetitiveRating.initial();
                CompetitiveRating rating = new CompetitiveRating(result.getDouble(1), result.getDouble(2), result.getInt(3));
                long storedSeason = result.getLong(4);
                return result.wasNull() || storedSeason != seasonId ? RatingCalculator.softReset(rating) : rating;
            }
        }
    }

    private double averageRating(Connection connection, List<Participant> participants, String team, long season) throws SQLException {
        double sum = 0; int count = 0;
        for (Participant participant : participants) if (participant.team().equals(team)) {
            sum += loadRating(connection, participant.playerId(), season).value(); count++;
        }
        return count == 0 ? 1500 : sum / count;
    }

    private void saveRating(Connection connection, UUID playerId, long seasonId, CompetitiveRating rating) throws SQLException {
        try (PreparedStatement save = connection.prepareStatement("""
                INSERT INTO tropicube_sheepwars_ratings
                    (player_uuid, rating, uncertainty, placements_remaining, season_id, updated_at)
                VALUES (?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE rating=VALUES(rating),
                    uncertainty=VALUES(uncertainty), placements_remaining=VALUES(placements_remaining),
                    season_id=VALUES(season_id), updated_at=VALUES(updated_at)
                """)) {
            save.setString(1, playerId.toString()); save.setDouble(2, rating.value());
            save.setDouble(3, rating.uncertainty()); save.setInt(4, rating.placementsRemaining());
            save.setLong(5, seasonId); save.setLong(6, System.currentTimeMillis()); save.executeUpdate();
        }
    }

    private void insertParticipant(Connection connection, UUID matchId, Participant participant,
                                   Double before, Double after, long networkExperience) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO tropicube_sheepwars_match_players
                    (match_id, player_uuid, team, kit, kills, deaths, sheep_launched,
                     damage_dealt, support_score, rating_before, rating_after, network_xp)
                VALUES (?, ?, ?, ?, ?, ?, ?, 0, 0, ?, ?, ?)
                """)) {
            insert.setString(1, matchId.toString()); insert.setString(2, participant.playerId().toString());
            insert.setString(3, participant.team()); insert.setString(4, participant.kit().name());
            insert.setInt(5, participant.kills()); insert.setInt(6, participant.survived() ? 0 : 1);
            insert.setInt(7, participant.sheepLaunched());
            if (before == null) insert.setNull(8, java.sql.Types.DOUBLE); else insert.setDouble(8, before);
            if (after == null) insert.setNull(9, java.sql.Types.DOUBLE); else insert.setDouble(9, after);
            insert.setLong(10, networkExperience); insert.executeUpdate();
        }
    }

    private void addMastery(Connection connection, Participant participant, String winner) throws SQLException {
        long gained = 100L + participant.kills() * 25L + participant.sheepLaunched() * 2L
                + (participant.team().equals(winner) ? 50 : 0);
        try (PreparedStatement update = connection.prepareStatement("""
                INSERT INTO tropicube_sheepwars_kit_mastery
                    (player_uuid, kit_id, experience, level, selected_branch, selected_catalog_version, updated_at)
                VALUES (?, ?, ?, ?, NULL, ?, ?) ON DUPLICATE KEY UPDATE
                    experience=experience + VALUES(experience),
                    level=1 + FLOOR(SQRT(experience / 250)), updated_at=VALUES(updated_at)
                """)) {
            update.setString(1, participant.playerId().toString()); update.setString(2, participant.kit().name());
            update.setLong(3, gained); update.setInt(4, 1 + (int) Math.sqrt(gained / 250.0));
            update.setInt(5, masteryCatalog.version()); update.setLong(6, System.currentTimeMillis());
            update.executeUpdate();
        }
    }

    private Penalty recordAbandonNow(UUID playerId) throws SQLException {
        PenaltyState previous = loadPenaltyState(playerId);
        long now = System.currentTimeMillis();
        int strikes = previous.lastAbandonAt() > 0 && now - previous.lastAbandonAt() < STRIKE_WINDOW
                ? Math.min(PENALTIES.length, previous.recentStrikes() + 1) : 1;
        long until = now + PENALTIES[strikes - 1];
        database.executeUpdate("""
                INSERT INTO tropicube_sheepwars_abandons(player_uuid, recent_strikes, last_abandon_at, penalty_until)
                VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE recent_strikes=VALUES(recent_strikes),
                    last_abandon_at=VALUES(last_abandon_at), penalty_until=VALUES(penalty_until)
                """, playerId.toString(), strikes, now, until);
        return new Penalty(strikes, until);
    }

    private Penalty loadPenalty(UUID playerId) throws SQLException {
        PenaltyState state = loadPenaltyState(playerId);
        return new Penalty(state.recentStrikes(), state.until());
    }

    private PenaltyState loadPenaltyState(UUID playerId) throws SQLException {
        try (Connection connection = database.getConnection(); PreparedStatement select = connection.prepareStatement(
                "SELECT recent_strikes, last_abandon_at, penalty_until FROM tropicube_sheepwars_abandons WHERE player_uuid = ?")) {
            select.setString(1, playerId.toString());
            try (ResultSet result = select.executeQuery()) {
                return result.next() ? new PenaltyState(result.getInt(1), result.getLong(2), result.getLong(3))
                        : new PenaltyState(0, 0, 0);
            }
        }
    }

    private SummaryVisibility parseVisibility(String value) {
        try { return SummaryVisibility.valueOf(value); }
        catch (IllegalArgumentException | NullPointerException ignored) { return SummaryVisibility.PUBLIC; }
    }
}
