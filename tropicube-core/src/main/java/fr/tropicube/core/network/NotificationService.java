package fr.tropicube.core.network;

import com.google.gson.Gson;
import fr.tropicube.core.managers.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Durable notification inbox shared by all Paper backends. */
public final class NotificationService {
    private static final Gson GSON = new Gson();

    public record Notification(long id, String category, String messageKey, List<String> arguments,
                               String actionJson, long createdAt, long expiresAt, boolean read) {}

    private final DatabaseManager database;

    public NotificationService(DatabaseManager database) {
        this.database = database;
    }

    public CompletableFuture<Void> create(UUID playerId, String category, String messageKey,
                                          List<String> arguments, String actionJson, long expiresAt) {
        return database.supplyAsync(() -> {
            database.executeUpdate("""
                    INSERT INTO tropicube_notifications
                        (player_uuid, category, message_key, arguments_json, action_json,
                         created_at, expires_at, read_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, NULL)
                    """, playerId.toString(), category, messageKey, GSON.toJson(arguments), actionJson,
                    System.currentTimeMillis(), expiresAt);
            return null;
        });
    }

    public CompletableFuture<List<Notification>> inbox(UUID playerId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return database.supplyAsync(() -> loadInbox(playerId, safeLimit));
    }

    public CompletableFuture<Void> markRead(UUID playerId, long notificationId) {
        return database.supplyAsync(() -> {
            database.executeUpdate("UPDATE tropicube_notifications SET read_at = ? WHERE id = ? AND player_uuid = ?",
                    System.currentTimeMillis(), notificationId, playerId.toString());
            return null;
        });
    }

    public CompletableFuture<Integer> purgeExpired() {
        return database.supplyAsync(() -> database.executeUpdate(
                "DELETE FROM tropicube_notifications WHERE expires_at <= ?", System.currentTimeMillis()));
    }

    private List<Notification> loadInbox(UUID playerId, int limit) throws SQLException {
        List<Notification> values = new ArrayList<>();
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT id, category, message_key, arguments_json, action_json,
                            created_at, expires_at, read_at
                     FROM tropicube_notifications
                     WHERE player_uuid = ? AND expires_at > ?
                     ORDER BY read_at IS NULL DESC, created_at DESC LIMIT ?
                     """)) {
            statement.setString(1, playerId.toString());
            statement.setLong(2, System.currentTimeMillis());
            statement.setInt(3, limit);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    String[] arguments = GSON.fromJson(result.getString("arguments_json"), String[].class);
                    values.add(new Notification(result.getLong("id"), result.getString("category"),
                            result.getString("message_key"), List.of(arguments), result.getString("action_json"),
                            result.getLong("created_at"), result.getLong("expires_at"),
                            result.getObject("read_at") != null));
                }
            }
        }
        return List.copyOf(values);
    }
}
