package fr.tropicube.core.network;

import fr.tropicube.core.managers.DatabaseManager;
import fr.tropicube.core.social.FriendshipRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Aggregates a player profile while enforcing its persistent visibility. */
public final class ProfileService {
    public enum Access { FULL, SUMMARY, HIDDEN }
    public record ProfileTitle(String id, String displayKey) {}
    public record Badge(String id, String displayKey, long unlockedAt) {}
    public record SeasonArchive(String seasonKey, double rating, String tier, int rankedMatches) {}
    public record KitMastery(String kitId, long experience, int level, String branch) {}
    public record Profile(UUID playerId, String username, String grade, long playTimeSeconds,
                          long networkExperience, int networkLevel, double balance, int friends,
                          String guildName, long matches, long wins, long kills, double rating,
                          String selectedTitleId, String selectedTitleKey, List<ProfileTitle> titles,
                          List<Badge> badges, List<SeasonArchive> seasonArchives,
                          List<KitMastery> kitMasteries, Access access) {
        public Profile {
            titles = List.copyOf(titles);
            badges = List.copyOf(badges); seasonArchives = List.copyOf(seasonArchives);
            kitMasteries = List.copyOf(kitMasteries);
        }
    }

    private final DatabaseManager database;
    private final PlayerPreferenceService preferences;
    private final FriendshipRepository friendships;

    public ProfileService(DatabaseManager database, PlayerPreferenceService preferences) {
        this.database = database;
        this.preferences = preferences;
        this.friendships = new FriendshipRepository(database);
    }

    public CompletableFuture<Profile> view(UUID viewerId, UUID targetId) {
        return preferences.load(targetId).thenCompose(setting -> database.supplyAsync(() -> {
            boolean self = viewerId.equals(targetId);
            boolean friends = self || friendships.areFriends(viewerId, targetId);
            Access access = self ? Access.FULL : switch (setting.profileVisibility()) {
                case PRIVATE -> Access.HIDDEN;
                case FRIENDS -> friends ? Access.FULL : Access.HIDDEN;
                case SUMMARY -> Access.SUMMARY;
            };
            return load(targetId, access);
        }));
    }

    public CompletableFuture<Boolean> selectTitle(UUID playerId, String titleId) {
        if (titleId == null || !titleId.matches("[a-z0-9_-]{1,96}")) return CompletableFuture.completedFuture(false);
        return database.supplyAsync(() -> {
            try (Connection connection = database.getConnection(); PreparedStatement owned = connection.prepareStatement(
                    "SELECT 1 FROM tropicube_profile_titles WHERE player_uuid = ? AND title_id = ?")) {
                owned.setString(1, playerId.toString()); owned.setString(2, titleId);
                try (ResultSet result = owned.executeQuery()) { if (!result.next()) return false; }
            }
            database.executeUpdate("""
                    INSERT INTO tropicube_player_comfort(player_uuid, selected_title_id, reroll_tokens, updated_at)
                    VALUES (?, ?, 0, ?) ON DUPLICATE KEY UPDATE selected_title_id=VALUES(selected_title_id),
                    updated_at=VALUES(updated_at)
                    """, playerId.toString(), titleId, System.currentTimeMillis());
            return true;
        });
    }

    private Profile load(UUID targetId, Access access) throws SQLException {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT p.username, p.grade, p.play_time,
                            COALESCE(np.experience, 0) network_experience, COALESCE(np.level, 1) network_level,
                            COALESCE(e.balance, 0) balance,
                            (SELECT COUNT(*) FROM tropicube_friendships f
                             WHERE (f.player_a = p.uuid OR f.player_b = p.uuid) AND f.status = 'ACCEPTED') friends,
                            g.name guild_name,
                            COUNT(DISTINCT mp.match_id) matches,
                            COALESCE(SUM(CASE WHEN m.winning_team = mp.team THEN 1 ELSE 0 END), 0) wins,
                            COALESCE(SUM(mp.kills), 0) kills,
                            COALESCE(r.rating, 1500) rating
                     FROM tropicube_players p
                     LEFT JOIN tropicube_network_progression np ON np.player_uuid = p.uuid
                     LEFT JOIN tropicube_economy e ON e.uuid = p.uuid
                     LEFT JOIN tropicube_guild_members gm ON gm.player_uuid = p.uuid
                     LEFT JOIN tropicube_guilds g ON g.id = gm.guild_id
                     LEFT JOIN tropicube_sheepwars_match_players mp ON mp.player_uuid = p.uuid
                     LEFT JOIN tropicube_sheepwars_matches m ON m.id = mp.match_id
                     LEFT JOIN tropicube_sheepwars_ratings r ON r.player_uuid = p.uuid
                     WHERE p.uuid = ?
                     GROUP BY p.uuid, p.username, p.grade, p.play_time, np.experience, np.level,
                              e.balance, g.name, r.rating
                     """)) {
            statement.setString(1, targetId.toString());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return null;
                SelectedTitle title = selectedTitle(connection, targetId);
                List<ProfileTitle> titles = access == Access.FULL ? titles(connection, targetId) : List.of();
                List<Badge> badges = access == Access.FULL ? badges(connection, targetId) : List.of();
                List<SeasonArchive> archives = access == Access.FULL ? archives(connection, targetId) : List.of();
                List<KitMastery> masteries = access == Access.FULL ? masteries(connection, targetId) : List.of();
                return new Profile(targetId, result.getString("username"), result.getString("grade"),
                        result.getLong("play_time"), result.getLong("network_experience"),
                        result.getInt("network_level"), result.getDouble("balance"), result.getInt("friends"),
                        result.getString("guild_name"), result.getLong("matches"), result.getLong("wins"),
                        result.getLong("kills"), result.getDouble("rating"),
                        title == null ? null : title.id(), title == null ? null : title.displayKey(),
                        titles, badges, archives, masteries, access);
            }
        }
    }

    private SelectedTitle selectedTitle(Connection connection, UUID playerId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT title.title_id, title.display_key FROM tropicube_player_comfort comfort
                JOIN tropicube_profile_titles title ON title.player_uuid = comfort.player_uuid
                    AND title.title_id = comfort.selected_title_id
                WHERE comfort.player_uuid = ?
                """)) {
            statement.setString(1, playerId.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? new SelectedTitle(result.getString(1), result.getString(2)) : null;
            }
        }
    }

    private List<ProfileTitle> titles(Connection connection, UUID playerId) throws SQLException {
        List<ProfileTitle> values = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT title_id, display_key FROM tropicube_profile_titles
                WHERE player_uuid=? ORDER BY unlocked_at DESC LIMIT 9
                """)) {
            statement.setString(1, playerId.toString());
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) values.add(new ProfileTitle(result.getString(1), result.getString(2)));
            }
        }
        return List.copyOf(values);
    }

    private List<Badge> badges(Connection connection, UUID playerId) throws SQLException {
        List<Badge> values = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT badge_id, display_key, unlocked_at FROM tropicube_profile_badges
                WHERE player_uuid = ? ORDER BY unlocked_at DESC LIMIT 3
                """)) {
            statement.setString(1, playerId.toString());
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) values.add(new Badge(result.getString(1), result.getString(2), result.getLong(3)));
            }
        }
        return List.copyOf(values);
    }

    private List<SeasonArchive> archives(Connection connection, UUID playerId) throws SQLException {
        List<SeasonArchive> values = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT season.season_key, archive.final_rating, archive.final_tier, archive.ranked_matches
                FROM tropicube_sheepwars_season_ratings archive
                JOIN tropicube_seasons season ON season.id = archive.season_id
                WHERE archive.player_uuid = ? ORDER BY season.ends_at DESC LIMIT 12
                """)) {
            statement.setString(1, playerId.toString());
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) values.add(new SeasonArchive(result.getString(1), result.getDouble(2),
                        result.getString(3), result.getInt(4)));
            }
        }
        return List.copyOf(values);
    }

    private List<KitMastery> masteries(Connection connection, UUID playerId) throws SQLException {
        List<KitMastery> values = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT kit_id, experience, level, selected_branch FROM tropicube_sheepwars_kit_mastery
                WHERE player_uuid = ? ORDER BY level DESC, experience DESC, kit_id
                """)) {
            statement.setString(1, playerId.toString());
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) values.add(new KitMastery(result.getString(1), result.getLong(2),
                        result.getInt(3), result.getString(4)));
            }
        }
        return List.copyOf(values);
    }

    private record SelectedTitle(String id, String displayKey) { }
}
