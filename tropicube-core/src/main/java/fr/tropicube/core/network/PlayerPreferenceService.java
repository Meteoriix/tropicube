package fr.tropicube.core.network;

import fr.tropicube.core.managers.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Persistent network-wide player privacy and presentation preferences. */
public final class PlayerPreferenceService {
    public enum ProfileVisibility { SUMMARY, FRIENDS, PRIVATE }
    public enum MessagePrivacy { EVERYONE, FRIENDS_PARTY, NOBODY }
    public enum LobbyVisibility { EVERYONE, FRIENDS, PARTY, NOBODY }

    public record Preferences(ProfileVisibility profileVisibility, MessagePrivacy messagePrivacy,
                              boolean globalChatEnabled, LobbyVisibility lobbyVisibility,
                              boolean contextualHelp, boolean lobbyEffectsEnabled) {
        public static Preferences defaults() {
            return new Preferences(ProfileVisibility.SUMMARY, MessagePrivacy.EVERYONE,
                    true, LobbyVisibility.EVERYONE, true, true);
        }
    }

    private final DatabaseManager database;

    public PlayerPreferenceService(DatabaseManager database) {
        this.database = database;
    }

    public CompletableFuture<Preferences> load(UUID playerId) {
        return database.supplyAsync(() -> loadNow(playerId));
    }

    public CompletableFuture<Void> save(UUID playerId, Preferences value) {
        return database.supplyAsync(() -> {
            database.executeUpdate("""
                    INSERT INTO tropicube_player_preferences
                         (player_uuid, profile_visibility, message_privacy, global_chat_enabled,
                         lobby_visibility, contextual_help, lobby_effects_enabled, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE profile_visibility=VALUES(profile_visibility),
                        message_privacy=VALUES(message_privacy), global_chat_enabled=VALUES(global_chat_enabled),
                        lobby_visibility=VALUES(lobby_visibility), contextual_help=VALUES(contextual_help),
                        lobby_effects_enabled=VALUES(lobby_effects_enabled),
                        updated_at=VALUES(updated_at)
                    """, playerId.toString(), value.profileVisibility().name(), value.messagePrivacy().name(),
                    value.globalChatEnabled(), value.lobbyVisibility().name(), value.contextualHelp(),
                    value.lobbyEffectsEnabled(),
                    System.currentTimeMillis());
            return null;
        });
    }

    private Preferences loadNow(UUID playerId) throws SQLException {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT profile_visibility, message_privacy, global_chat_enabled,
                            lobby_visibility, contextual_help, lobby_effects_enabled
                     FROM tropicube_player_preferences WHERE player_uuid = ?
                     """)) {
            statement.setString(1, playerId.toString());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return Preferences.defaults();
                try {
                    return new Preferences(ProfileVisibility.valueOf(result.getString(1)),
                            MessagePrivacy.valueOf(result.getString(2)), result.getBoolean(3),
                            LobbyVisibility.valueOf(result.getString(4)), result.getBoolean(5), result.getBoolean(6));
                } catch (IllegalArgumentException error) {
                    return Preferences.defaults();
                }
            }
        }
    }
}
