package fr.tropicube.core.network;

import fr.tropicube.core.managers.DatabaseManager;
import fr.tropicube.core.social.FriendshipRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Aggregates a player profile while enforcing its persistent visibility. */
public final class ProfileService {
    public enum Access { FULL, SUMMARY, HIDDEN }
    public record Profile(UUID playerId, String username, String grade, long playTimeSeconds,
                          long networkExperience, int networkLevel, double balance, int friends,
                          String guildName, long matches, long wins, long kills, double rating, Access access) {}

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
                return new Profile(targetId, result.getString("username"), result.getString("grade"),
                        result.getLong("play_time"), result.getLong("network_experience"),
                        result.getInt("network_level"), result.getDouble("balance"), result.getInt("friends"),
                        result.getString("guild_name"), result.getLong("matches"), result.getLong("wins"),
                        result.getLong("kills"), result.getDouble("rating"), access);
            }
        }
    }
}
