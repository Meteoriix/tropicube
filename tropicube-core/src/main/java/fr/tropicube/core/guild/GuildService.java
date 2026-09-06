package fr.tropicube.core.guild;

import com.google.gson.Gson;
import fr.tropicube.core.managers.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Durable guild membership, roles, capped contributions, weekly challenges and succession. */
public final class GuildService {
    public static final int OWNER_INACTIVITY_DAYS = 30;
    public enum Role { OWNER, OFFICER, MEMBER }
    public enum Result { SUCCESS, NOT_MEMBER, NOT_ALLOWED, ALREADY_MEMBER, FULL, INVALID, NOT_FOUND, LIMIT_REACHED }
    public record Member(UUID playerId, String username, Role role, long joinedAt,
                         long lastActiveAt, long weeklyContribution) {}
    public record Challenge(String id, long progress, long target, boolean completed) {}
    public record Guild(long id, String name, String tag, UUID ownerId, int level, long experience,
                        List<Member> members, List<Challenge> challenges) {}
    public record Ranking(String name, String tag, double score, int rankedMatches) {}
    private static final Gson GSON = new Gson();
    private static final ZoneId ZONE = ZoneId.of("Europe/Paris");

    private final DatabaseManager database;
    private final int maximumMembers;
    private final int maximumOfficers;
    private final long weeklyContributionCap;

    public GuildService(DatabaseManager database, int maximumMembers, int maximumOfficers,
                        long weeklyContributionCap) {
        this.database = database;
        this.maximumMembers = maximumMembers;
        this.maximumOfficers = maximumOfficers;
        this.weeklyContributionCap = weeklyContributionCap;
    }

    /** Invitation data contains no player object and can cross the asynchronous boundary. */
    public record Invitation(long guildId, String name, String tag, long expiresAt) { }

    /** Maximum membership enforced by every join operation. */
    public int maximumMembers() { return maximumMembers; }
    /** Maximum officer count enforced by promotions. */
    public int maximumOfficers() { return maximumOfficers; }
    /** Maximum XP contribution accepted from one member during the ISO week. */
    public long weeklyContributionCap() { return weeklyContributionCap; }

    /** Lists only invitations that can still be accepted, newest first. */
    public CompletableFuture<List<Invitation>> invitations(UUID player) {
        return database.supplyAsync(() -> {
            try (Connection connection = database.getConnection(); PreparedStatement statement = connection.prepareStatement("""
                    SELECT g.id, g.name, g.tag, i.expires_at FROM tropicube_guild_invites i
                    JOIN tropicube_guilds g ON g.id = i.guild_id
                    WHERE i.player_uuid = ? AND i.expires_at > ? ORDER BY i.created_at DESC, g.id
                    """)) {
                statement.setString(1, player.toString());
                statement.setLong(2, System.currentTimeMillis());
                List<Invitation> invitations = new ArrayList<>();
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) invitations.add(new Invitation(rows.getLong(1), rows.getString(2),
                            rows.getString(3), rows.getLong(4)));
                }
                return List.copyOf(invitations);
            }
        });
    }

    /** Administrative operations exposed to the Lobby without depending on its view types. */
    public enum Administration { INVITE, LEAVE, KICK, PROMOTE, DEMOTE, TRANSFER }

    /** Checks the screen's guild identity inside the same lock as the mutation. */
    public CompletableFuture<Result> administer(UUID actor, long expectedGuild, Administration action, UUID target) {
        if (actor == null || action == null || expectedGuild <= 0 || action != Administration.LEAVE && target == null)
            return CompletableFuture.completedFuture(Result.INVALID);
        return database.supplyAsync(() -> switch (action) {
            case INVITE -> inviteNow(actor, target, expectedGuild);
            case LEAVE -> leaveNow(actor, expectedGuild);
            case KICK -> removeMember(actor, target, expectedGuild);
            case PROMOTE -> setRoleNow(actor, target, Role.OFFICER, expectedGuild);
            case DEMOTE -> setRoleNow(actor, target, Role.MEMBER, expectedGuild);
            case TRANSFER -> transferNow(actor, target, expectedGuild);
        });
    }

    public CompletableFuture<Result> create(UUID owner, String name, String tag) {
        return database.supplyAsync(() -> createNow(owner, name, tag));
    }
    public CompletableFuture<Guild> guild(UUID player) { return database.supplyAsync(() -> loadByPlayer(player)); }
    public CompletableFuture<Result> invite(UUID actor, UUID target) {
        return database.supplyAsync(() -> inviteNow(actor, target));
    }
    public CompletableFuture<Result> accept(UUID player, String tag) {
        return database.supplyAsync(() -> acceptNow(player, tag));
    }
    public CompletableFuture<Result> leave(UUID player) { return database.supplyAsync(() -> leaveNow(player)); }
    public CompletableFuture<Result> kick(UUID actor, UUID target) {
        return database.supplyAsync(() -> removeMember(actor, target));
    }
    public CompletableFuture<Result> setRole(UUID actor, UUID target, Role role) {
        return database.supplyAsync(() -> setRoleNow(actor, target, role));
    }
    public CompletableFuture<Result> transfer(UUID owner, UUID target) {
        return database.supplyAsync(() -> transferNow(owner, target));
    }
    public CompletableFuture<Long> contribute(UUID player, long amount) {
        if (amount <= 0) return CompletableFuture.completedFuture(0L);
        return database.supplyAsync(() -> contributeNow(player, amount));
    }
    public CompletableFuture<Integer> applySuccession() {
        return database.supplyAsync(this::applySuccessionNow);
    }
    public CompletableFuture<Void> touch(UUID player) {
        return database.supplyAsync(() -> {
            database.executeUpdate("UPDATE tropicube_guild_members SET last_active_at = ? WHERE player_uuid = ?",
                    System.currentTimeMillis(), player.toString());
            return null;
        });
    }
    public CompletableFuture<Void> recordRankedResult(UUID player, long seasonId, double ratingDelta) {
        return database.supplyAsync(() -> {
            try (Connection connection = database.getConnection()) {
                Long guildId = memberGuildId(connection, player);
                if (guildId == null) return null;
                database.executeUpdate("""
                        INSERT INTO tropicube_guild_season_scores(guild_id, season_id, score, ranked_matches, updated_at)
                        VALUES (?, ?, ?, 1, ?)
                        ON DUPLICATE KEY UPDATE score = score + VALUES(score),
                            ranked_matches = ranked_matches + 1, updated_at = VALUES(updated_at)
                        """, guildId, seasonId, ratingDelta, System.currentTimeMillis());
                ensureChallenges(connection, guildId, weekKey());
                database.executeUpdate("""
                        UPDATE tropicube_guild_challenges SET progress = LEAST(target, progress + 1),
                            completed_at = CASE WHEN progress + 1 >= target THEN COALESCE(completed_at, ?) ELSE completed_at END
                        WHERE guild_id = ? AND week_key = ? AND challenge_id = 'RANKED_MATCHES'
                        """, System.currentTimeMillis(), guildId, weekKey());
                return null;
            }
        });
    }
    public CompletableFuture<List<Ranking>> ranking(long seasonId, int limit) {
        return database.supplyAsync(() -> loadRanking(seasonId, Math.max(1, Math.min(limit, 100))));
    }
    public CompletableFuture<List<Ranking>> currentRanking(int limit) {
        return database.supplyAsync(() -> {
            try (Connection connection = database.getConnection(); PreparedStatement statement = connection.prepareStatement(
                    "SELECT id FROM tropicube_seasons WHERE season_key LIKE 'SW-%' AND status = 'ACTIVE' ORDER BY starts_at DESC LIMIT 1")) {
                try (ResultSet result = statement.executeQuery()) {
                    return result.next() ? loadRanking(result.getLong(1), Math.max(1, Math.min(limit, 100))) : List.of();
                }
            }
        });
    }

    private Result createNow(UUID owner, String rawName, String rawTag) throws SQLException {
        String name = rawName == null ? "" : rawName.trim();
        String tag = GuildNames.normalizeTag(rawTag);
        if (!GuildNames.validName(name) || !GuildNames.validTag(tag)) return Result.INVALID;
        try (Connection connection = database.getConnection();
             GuildMutationLock lock = GuildMutationLock.acquire(connection)) {
            connection.setAutoCommit(false);
            try {
                if (memberGuildId(connection, owner) != null) { connection.rollback(); return Result.ALREADY_MEMBER; }
                long now = System.currentTimeMillis();
                long guildId;
                try (PreparedStatement insert = connection.prepareStatement("""
                        INSERT INTO tropicube_guilds(name, tag, owner_uuid, level, experience, created_at, updated_at)
                        VALUES (?, ?, ?, 1, 0, ?, ?)
                        """, Statement.RETURN_GENERATED_KEYS)) {
                    insert.setString(1, name); insert.setString(2, tag); insert.setString(3, owner.toString());
                    insert.setLong(4, now); insert.setLong(5, now); insert.executeUpdate();
                    try (ResultSet keys = insert.getGeneratedKeys()) {
                        if (!keys.next()) throw new SQLException("Identifiant de guilde absent");
                        guildId = keys.getLong(1);
                    }
                }
                insertMember(connection, guildId, owner, Role.OWNER, now);
                audit(connection, guildId, owner, "CREATE", Map.of("name", name, "tag", tag));
                connection.commit();
                return Result.SUCCESS;
            } catch (SQLException error) {
                connection.rollback();
                return isDuplicate(error) ? Result.INVALID : throwSql(error);
            } finally { connection.setAutoCommit(true); }
        }
    }

    private Result inviteNow(UUID actor, UUID target) throws SQLException {
        return inviteNow(actor, target, null);
    }

    private Result inviteNow(UUID actor, UUID target, Long expectedGuild) throws SQLException {
        try (Connection connection = database.getConnection();
             GuildMutationLock lock = GuildMutationLock.acquire(connection)) {
            if (expectedGuild != null && !expectedGuild.equals(memberGuildId(connection, actor))) return Result.NOT_FOUND;
            MemberRow member = member(connection, actor);
            if (member == null) return Result.NOT_MEMBER;
            if (!GuildPermissions.canInvite(member.role())) return Result.NOT_ALLOWED;
            if (memberGuildId(connection, target) != null) return Result.ALREADY_MEMBER;
            if (memberCount(connection, member.guildId()) >= maximumMembers) return Result.FULL;
            long now = System.currentTimeMillis();
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO tropicube_guild_invites(guild_id, player_uuid, invited_by, created_at, expires_at)
                    VALUES (?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE invited_by=VALUES(invited_by), created_at=VALUES(created_at), expires_at=VALUES(expires_at)
                    """)) {
                statement.setLong(1, member.guildId()); statement.setString(2, target.toString());
                statement.setString(3, actor.toString()); statement.setLong(4, now);
                statement.setLong(5, now + 7L * 24 * 60 * 60 * 1000); statement.executeUpdate();
            }
            connection.commit();
            return Result.SUCCESS;
        }
    }

    private Result acceptNow(UUID player, String rawTag) throws SQLException {
        String tag = GuildNames.normalizeTag(rawTag);
        try (Connection connection = database.getConnection();
             GuildMutationLock lock = GuildMutationLock.acquire(connection)) {
            connection.setAutoCommit(false);
            try {
                if (memberGuildId(connection, player) != null) { connection.rollback(); return Result.ALREADY_MEMBER; }
                Long guildId = null;
                try (PreparedStatement select = connection.prepareStatement("""
                        SELECT invite.guild_id FROM tropicube_guild_invites invite
                        JOIN tropicube_guilds guild ON guild.id = invite.guild_id
                        WHERE invite.player_uuid = ? AND invite.expires_at > ? AND guild.tag = ? FOR UPDATE
                        """)) {
                    select.setString(1, player.toString()); select.setLong(2, System.currentTimeMillis()); select.setString(3, tag);
                    try (ResultSet result = select.executeQuery()) { if (result.next()) guildId = result.getLong(1); }
                }
                if (guildId == null) { connection.rollback(); return Result.NOT_FOUND; }
                if (memberCount(connection, guildId) >= maximumMembers) { connection.rollback(); return Result.FULL; }
                long now = System.currentTimeMillis();
                insertMember(connection, guildId, player, Role.MEMBER, now);
                try (PreparedStatement delete = connection.prepareStatement(
                        "DELETE FROM tropicube_guild_invites WHERE player_uuid = ?")) {
                    delete.setString(1, player.toString()); delete.executeUpdate();
                }
                audit(connection, guildId, player, "JOIN", Map.of());
                connection.commit();
                return Result.SUCCESS;
            } catch (SQLException error) { connection.rollback(); throw error; }
            finally { connection.setAutoCommit(true); }
        }
    }

    private Result leaveNow(UUID player) throws SQLException {
        return leaveNow(player, null);
    }

    private Result leaveNow(UUID player, Long expectedGuild) throws SQLException {
        try (Connection connection = database.getConnection();
             GuildMutationLock lock = GuildMutationLock.acquire(connection)) {
            if (expectedGuild != null && !expectedGuild.equals(memberGuildId(connection, player))) return Result.NOT_FOUND;
            MemberRow row = member(connection, player);
            if (row == null) return Result.NOT_MEMBER;
            if (row.role() == Role.OWNER && memberCount(connection, row.guildId()) > 1) return Result.NOT_ALLOWED;
            if (row.role() == Role.OWNER) {
                update(connection, "DELETE FROM tropicube_guilds WHERE id = ?", row.guildId());
            } else {
                update(connection, "DELETE FROM tropicube_guild_members WHERE player_uuid = ?", player.toString());
                audit(connection, row.guildId(), player, "LEAVE", Map.of());
            }
            connection.commit();
            return Result.SUCCESS;
        }
    }

    private Result removeMember(UUID actor, UUID target) throws SQLException {
        return removeMember(actor, target, null);
    }

    private Result removeMember(UUID actor, UUID target, Long expectedGuild) throws SQLException {
        if (actor.equals(target)) return Result.INVALID;
        try (Connection connection = database.getConnection();
             GuildMutationLock lock = GuildMutationLock.acquire(connection)) {
            if (expectedGuild != null && !expectedGuild.equals(memberGuildId(connection, actor))) return Result.NOT_FOUND;
            MemberRow source = member(connection, actor), destination = member(connection, target);
            if (source == null || destination == null || source.guildId() != destination.guildId()) return Result.NOT_FOUND;
            if (!GuildPermissions.canKick(source.role(), destination.role())) return Result.NOT_ALLOWED;
            update(connection, "DELETE FROM tropicube_guild_members WHERE player_uuid = ?", target.toString());
            audit(connection, source.guildId(), actor, "KICK", Map.of("target", target.toString()));
            connection.commit();
            return Result.SUCCESS;
        }
    }

    private Result setRoleNow(UUID actor, UUID target, Role role) throws SQLException {
        return setRoleNow(actor, target, role, null);
    }

    private Result setRoleNow(UUID actor, UUID target, Role role, Long expectedGuild) throws SQLException {
        if (role == Role.OWNER) return Result.INVALID;
        try (Connection connection = database.getConnection();
             GuildMutationLock lock = GuildMutationLock.acquire(connection)) {
            if (expectedGuild != null && !expectedGuild.equals(memberGuildId(connection, actor))) return Result.NOT_FOUND;
            MemberRow source = member(connection, actor), destination = member(connection, target);
            if (source == null || destination == null || source.guildId() != destination.guildId()) return Result.NOT_FOUND;
            if (!GuildPermissions.canManage(source.role(), destination.role())) return Result.NOT_ALLOWED;
            if (destination.role() == role) return Result.SUCCESS;
            if (role == Role.OFFICER && officerCount(connection, source.guildId()) >= maximumOfficers)
                return Result.LIMIT_REACHED;
            update(connection, "UPDATE tropicube_guild_members SET role = ? WHERE player_uuid = ?",
                    role.name(), target.toString());
            audit(connection, source.guildId(), actor, "ROLE", Map.of("target", target.toString(), "role", role.name()));
            connection.commit();
            return Result.SUCCESS;
        }
    }

    private Result transferNow(UUID owner, UUID target) throws SQLException {
        return transferNow(owner, target, null);
    }

    private Result transferNow(UUID owner, UUID target, Long expectedGuild) throws SQLException {
        try (Connection connection = database.getConnection();
             GuildMutationLock lock = GuildMutationLock.acquire(connection)) {
            if (expectedGuild != null && !expectedGuild.equals(memberGuildId(connection, owner))) return Result.NOT_FOUND;
            connection.setAutoCommit(false);
            try {
                MemberRow source = member(connection, owner), destination = member(connection, target);
                if (source == null || destination == null || source.guildId() != destination.guildId()) {
                    connection.rollback(); return Result.NOT_FOUND;
                }
                if (source.role() != Role.OWNER || destination.role() == Role.OWNER) {
                    connection.rollback(); return Result.NOT_ALLOWED;
                }
                setOwner(connection, source.guildId(), owner, target, "TRANSFER");
                connection.commit();
                return Result.SUCCESS;
            } catch (SQLException error) { connection.rollback(); throw error; }
            finally { connection.setAutoCommit(true); }
        }
    }

    private long contributeNow(UUID player, long amount) throws SQLException {
        try (Connection connection = database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                MemberRow member = member(connection, player);
                if (member == null) { connection.rollback(); return 0; }
                String week = weekKey();
                long used = member.contributionWeek().equals(week) ? member.weeklyContribution() : 0;
                long accepted = Math.min(amount, Math.max(0, weeklyContributionCap - used));
                if (accepted <= 0) { connection.rollback(); return 0; }
                try (PreparedStatement update = connection.prepareStatement("""
                        UPDATE tropicube_guild_members SET contribution_week = ?, weekly_contribution = ?,
                            total_contribution = total_contribution + ?, last_active_at = ? WHERE player_uuid = ?
                        """)) {
                    update.setString(1, week); update.setLong(2, used + accepted); update.setLong(3, accepted);
                    update.setLong(4, System.currentTimeMillis()); update.setString(5, player.toString()); update.executeUpdate();
                }
                try (PreparedStatement guild = connection.prepareStatement("""
                        UPDATE tropicube_guilds SET experience = experience + ?,
                            level = 1 + FLOOR(SQRT((experience + ?) / 10000)), updated_at = ? WHERE id = ?
                        """)) {
                    guild.setLong(1, accepted); guild.setLong(2, accepted);
                    guild.setLong(3, System.currentTimeMillis()); guild.setLong(4, member.guildId()); guild.executeUpdate();
                }
                ensureChallenges(connection, member.guildId(), week);
                try (PreparedStatement challenge = connection.prepareStatement("""
                        UPDATE tropicube_guild_challenges SET progress = LEAST(target, progress + ?),
                            completed_at = CASE WHEN progress + ? >= target THEN COALESCE(completed_at, ?) ELSE completed_at END
                        WHERE guild_id = ? AND week_key = ? AND challenge_id = 'CONTRIBUTION'
                        """)) {
                    challenge.setLong(1, accepted); challenge.setLong(2, accepted);
                    challenge.setLong(3, System.currentTimeMillis()); challenge.setLong(4, member.guildId());
                    challenge.setString(5, week); challenge.executeUpdate();
                }
                connection.commit();
                return accepted;
            } catch (SQLException | RuntimeException error) { connection.rollback(); throw error; }
            finally { connection.setAutoCommit(true); }
        }
    }

    private int applySuccessionNow() throws SQLException {
        long cutoff = System.currentTimeMillis() - OWNER_INACTIVITY_DAYS * 86_400_000L;
        List<Long> guilds = new ArrayList<>();
        try (Connection connection = database.getConnection(); PreparedStatement statement = connection.prepareStatement("""
                SELECT guild.id FROM tropicube_guilds guild
                JOIN tropicube_guild_members owner ON owner.guild_id = guild.id AND owner.player_uuid = guild.owner_uuid
                WHERE owner.last_active_at < ?
                """)) {
            statement.setLong(1, cutoff);
            try (ResultSet result = statement.executeQuery()) { while (result.next()) guilds.add(result.getLong(1)); }
        }
        int changed = 0;
        for (Long guildId : guilds) if (succeedOwner(guildId, cutoff)) changed++;
        return changed;
    }

    private boolean succeedOwner(long guildId, long cutoff) throws SQLException {
        try (Connection connection = database.getConnection();
             GuildMutationLock lock = GuildMutationLock.acquire(connection)) {
            connection.setAutoCommit(false);
            try {
                UUID oldOwner = null, successor = null;
                try (PreparedStatement statement = connection.prepareStatement("""
                        SELECT guild.owner_uuid,
                               (SELECT member.player_uuid FROM tropicube_guild_members member
                                WHERE member.guild_id = guild.id AND member.player_uuid <> guild.owner_uuid
                                  AND member.last_active_at >= ? ORDER BY member.joined_at, member.player_uuid LIMIT 1) successor
                        FROM tropicube_guilds guild WHERE guild.id = ? FOR UPDATE
                        """)) {
                    statement.setLong(1, cutoff); statement.setLong(2, guildId);
                    try (ResultSet result = statement.executeQuery()) {
                        if (result.next()) {
                            oldOwner = UUID.fromString(result.getString(1));
                            String value = result.getString(2); if (value != null) successor = UUID.fromString(value);
                        }
                    }
                }
                if (oldOwner == null || successor == null) { connection.rollback(); return false; }
                setOwner(connection, guildId, oldOwner, successor, "INACTIVITY_SUCCESSION");
                connection.commit();
                return true;
            } catch (SQLException error) { connection.rollback(); throw error; }
            finally { connection.setAutoCommit(true); }
        }
    }

    private Guild loadByPlayer(UUID player) throws SQLException {
        Long id;
        try (Connection connection = database.getConnection()) { id = memberGuildId(connection, player); }
        if (id == null) return null;
        try (Connection connection = database.getConnection(); PreparedStatement guild = connection.prepareStatement(
                "SELECT name, tag, owner_uuid, level, experience FROM tropicube_guilds WHERE id = ?")) {
            guild.setLong(1, id);
            try (ResultSet result = guild.executeQuery()) {
                if (!result.next()) return null;
                return new Guild(id, result.getString(1), result.getString(2), UUID.fromString(result.getString(3)),
                        result.getInt(4), result.getLong(5), members(connection, id), challenges(connection, id));
            }
        }
    }

    private List<Ranking> loadRanking(long seasonId, int limit) throws SQLException {
        List<Ranking> values = new ArrayList<>();
        try (Connection connection = database.getConnection(); PreparedStatement statement = connection.prepareStatement("""
                SELECT guild.name, guild.tag, score.score, score.ranked_matches
                FROM tropicube_guild_season_scores score JOIN tropicube_guilds guild ON guild.id = score.guild_id
                WHERE score.season_id = ? ORDER BY score.score DESC, score.ranked_matches DESC LIMIT ?
                """)) {
            statement.setLong(1, seasonId); statement.setInt(2, limit);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) values.add(new Ranking(result.getString(1), result.getString(2),
                        result.getDouble(3), result.getInt(4)));
            }
        }
        return List.copyOf(values);
    }

    private List<Member> members(Connection connection, long guildId) throws SQLException {
        List<Member> values = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT member.player_uuid, player.username, member.role, member.joined_at,
                       member.last_active_at, CASE WHEN member.contribution_week = ? THEN member.weekly_contribution ELSE 0 END
                FROM tropicube_guild_members member JOIN tropicube_players player ON player.uuid = member.player_uuid
                WHERE member.guild_id = ? ORDER BY FIELD(member.role, 'OWNER', 'OFFICER', 'MEMBER'), member.joined_at
                """)) {
            statement.setString(1, weekKey());
            statement.setLong(2, guildId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) values.add(new Member(UUID.fromString(result.getString(1)), result.getString(2),
                        Role.valueOf(result.getString(3)), result.getLong(4), result.getLong(5), result.getLong(6)));
            }
        }
        return List.copyOf(values);
    }

    private List<Challenge> challenges(Connection connection, long guildId) throws SQLException {
        ensureChallenges(connection, guildId, weekKey());
        List<Challenge> values = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT challenge_id, progress, target, completed_at FROM tropicube_guild_challenges
                WHERE guild_id = ? AND week_key = ? ORDER BY challenge_id
                """)) {
            statement.setLong(1, guildId); statement.setString(2, weekKey());
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) values.add(new Challenge(result.getString(1), result.getLong(2),
                        result.getLong(3), result.getObject(4) != null));
            }
        }
        return List.copyOf(values);
    }

    private void setOwner(Connection connection, long guildId, UUID oldOwner, UUID successor, String action) throws SQLException {
        try (PreparedStatement guild = connection.prepareStatement(
                "UPDATE tropicube_guilds SET owner_uuid = ?, updated_at = ? WHERE id = ?");
             PreparedStatement roles = connection.prepareStatement("""
                     UPDATE tropicube_guild_members SET role = CASE WHEN player_uuid = ? THEN 'OWNER'
                         WHEN player_uuid = ? THEN 'OFFICER' ELSE role END
                     WHERE guild_id = ? AND player_uuid IN (?, ?)
                     """)) {
            guild.setString(1, successor.toString()); guild.setLong(2, System.currentTimeMillis()); guild.setLong(3, guildId); guild.executeUpdate();
            roles.setString(1, successor.toString()); roles.setString(2, oldOwner.toString()); roles.setLong(3, guildId);
            roles.setString(4, successor.toString()); roles.setString(5, oldOwner.toString()); roles.executeUpdate();
        }
        audit(connection, guildId, null, action, Map.of("from", oldOwner.toString(), "to", successor.toString()));
    }

    private void ensureChallenges(Connection connection, long guildId, String week) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT IGNORE INTO tropicube_guild_challenges(guild_id, week_key, challenge_id, progress, target)
                VALUES (?, ?, 'CONTRIBUTION', 0, ?), (?, ?, 'RANKED_MATCHES', 0, 10)
                """)) {
            statement.setLong(1, guildId); statement.setString(2, week);
            statement.setLong(3, weeklyContributionCap * Math.min(maximumMembers, 10));
            statement.setLong(4, guildId); statement.setString(5, week); statement.executeUpdate();
        }
    }

    private void insertMember(Connection connection, long guildId, UUID player, Role role, long now) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO tropicube_guild_members
                    (guild_id, player_uuid, role, joined_at, last_active_at, contribution_week,
                     weekly_contribution, total_contribution)
                VALUES (?, ?, ?, ?, ?, '', 0, 0)
                """)) {
            statement.setLong(1, guildId); statement.setString(2, player.toString()); statement.setString(3, role.name());
            statement.setLong(4, now); statement.setLong(5, now); statement.executeUpdate();
        }
    }

    private MemberRow member(Connection connection, UUID player) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT guild_id, role, contribution_week, weekly_contribution FROM tropicube_guild_members WHERE player_uuid = ?")) {
            statement.setString(1, player.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? new MemberRow(result.getLong(1), Role.valueOf(result.getString(2)),
                        result.getString(3), result.getLong(4)) : null;
            }
        }
    }
    private Long memberGuildId(Connection c, UUID p) throws SQLException { MemberRow row = member(c, p); return row == null ? null : row.guildId(); }
    private int memberCount(Connection c, long id) throws SQLException { return count(c, "SELECT COUNT(*) FROM tropicube_guild_members WHERE guild_id = ?", id); }
    private int officerCount(Connection c, long id) throws SQLException { return count(c, "SELECT COUNT(*) FROM tropicube_guild_members WHERE guild_id = ? AND role = 'OFFICER'", id); }
    private int count(Connection c, String sql, long id) throws SQLException {
        try (PreparedStatement statement = c.prepareStatement(sql)) { statement.setLong(1, id);
            try (ResultSet result = statement.executeQuery()) { result.next(); return result.getInt(1); } }
    }
    private void audit(Connection c, long id, UUID actor, String action, Map<String, String> details) throws SQLException {
        try (PreparedStatement statement = c.prepareStatement("""
                INSERT INTO tropicube_guild_audit(guild_id, actor_uuid, action_type, details_json, created_at)
                VALUES (?, ?, ?, ?, ?)
                """)) {
            statement.setLong(1, id); statement.setString(2, actor == null ? null : actor.toString());
            statement.setString(3, action); statement.setString(4, GSON.toJson(details));
            statement.setLong(5, System.currentTimeMillis()); statement.executeUpdate();
        }
    }
    private static void update(Connection connection, String sql, Object... arguments) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < arguments.length; index++) statement.setObject(index + 1, arguments[index]);
            statement.executeUpdate();
        }
    }

    static String weekKey() { LocalDate d = LocalDate.now(ZONE); WeekFields f = WeekFields.ISO;
        return "%d-W%02d".formatted(d.get(f.weekBasedYear()), d.get(f.weekOfWeekBasedYear())); }
    private static boolean isDuplicate(SQLException error) { return "23000".equals(error.getSQLState()); }
    private static Result throwSql(SQLException error) throws SQLException { throw error; }
    private record MemberRow(long guildId, Role role, String contributionWeek, long weeklyContribution) {}
}
