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
    public static final long RETENTION_MILLIS = 7L * 24 * 60 * 60 * 1000;

    public enum ActionType { NONE, SUGGEST_COMMAND }
    public record Action(ActionType type, String value) {
        public Action {
            if (type == null) type = ActionType.NONE;
            if (value == null) value = "";
            if (type == ActionType.SUGGEST_COMMAND && !allowedCommand(value)) {
                throw new IllegalArgumentException("Action de notification non autorisée");
            }
        }
        public static Action none() { return new Action(ActionType.NONE, ""); }
    }

    public record Notification(long id, String category, String messageKey, List<String> arguments,
                               Action action, long createdAt, long expiresAt, boolean read) {}
    public record Page(List<Notification> notifications, int page, boolean hasNext, int unread) {
        public Page { notifications = List.copyOf(notifications); }
    }

    private final DatabaseManager database;

    public NotificationService(DatabaseManager database) {
        this.database = database;
    }

    public CompletableFuture<Void> create(UUID playerId, String category, String messageKey,
                                          List<String> arguments, Action action) {
        return database.supplyAsync(() -> {
            long now = System.currentTimeMillis();
            database.executeUpdate("""
                    INSERT INTO tropicube_notifications
                        (player_uuid, category, message_key, arguments_json, action_json,
                         created_at, expires_at, read_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, NULL)
                    """, playerId.toString(), category, messageKey, GSON.toJson(arguments),
                    GSON.toJson(action == null ? Action.none() : action), now, now + RETENTION_MILLIS);
            return null;
        });
    }

    public CompletableFuture<List<Notification>> inbox(UUID playerId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return database.supplyAsync(() -> loadInbox(playerId, safeLimit));
    }

    public CompletableFuture<Page> page(UUID playerId, String category, int page, int size) {
        int safePage = Math.max(0, page), safeSize = Math.max(1, Math.min(size, 45));
        String filter = category == null || category.isBlank() || category.equalsIgnoreCase("ALL")
                ? null : category.toUpperCase(java.util.Locale.ROOT);
        return database.supplyAsync(() -> loadPage(playerId, filter, safePage, safeSize));
    }

    public CompletableFuture<Void> markRead(UUID playerId, long notificationId) {
        return database.supplyAsync(() -> {
            database.executeUpdate("UPDATE tropicube_notifications SET read_at = ? WHERE id = ? AND player_uuid = ?",
                    System.currentTimeMillis(), notificationId, playerId.toString());
            return null;
        });
    }

    public CompletableFuture<Void> markAllRead(UUID playerId) {
        return database.supplyAsync(() -> {
            database.executeUpdate("UPDATE tropicube_notifications SET read_at = ? WHERE player_uuid = ? AND read_at IS NULL",
                    System.currentTimeMillis(), playerId.toString());
            return null;
        });
    }

    public CompletableFuture<Boolean> delete(UUID playerId, long notificationId) {
        return database.supplyAsync(() -> database.executeUpdate(
                "DELETE FROM tropicube_notifications WHERE id = ? AND player_uuid = ?",
                notificationId, playerId.toString()) == 1);
    }

    public CompletableFuture<Integer> deleteRead(UUID playerId) {
        return database.supplyAsync(() -> database.executeUpdate(
                "DELETE FROM tropicube_notifications WHERE player_uuid = ? AND read_at IS NOT NULL",
                playerId.toString()));
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
                            result.getString("message_key"), arguments == null ? List.of() : List.of(arguments), parseAction(result.getString("action_json")),
                            result.getLong("created_at"), result.getLong("expires_at"),
                            result.getObject("read_at") != null));
                }
            }
        }
        return List.copyOf(values);
    }

    private Page loadPage(UUID playerId, String category, int page, int size) throws SQLException {
        List<Notification> values = new ArrayList<>();
        String categoryClause = category == null ? "" : " AND category = ?";
        String sql = """
                SELECT id, category, message_key, arguments_json, action_json, created_at, expires_at, read_at
                FROM tropicube_notifications WHERE player_uuid = ? AND expires_at > ?
                """ + categoryClause + " ORDER BY read_at IS NULL DESC, created_at DESC LIMIT ? OFFSET ?";
        try (Connection connection = database.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            statement.setString(index++, playerId.toString());
            statement.setLong(index++, System.currentTimeMillis());
            if (category != null) statement.setString(index++, category);
            statement.setInt(index++, size + 1);
            statement.setInt(index, page * size);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    String[] arguments = GSON.fromJson(result.getString("arguments_json"), String[].class);
                    values.add(new Notification(result.getLong("id"), result.getString("category"),
                            result.getString("message_key"), arguments == null ? List.of() : List.of(arguments),
                            parseAction(result.getString("action_json")), result.getLong("created_at"),
                            result.getLong("expires_at"), result.getObject("read_at") != null));
                }
            }
        }
        int unread;
        try (Connection connection = database.getConnection(); PreparedStatement count = connection.prepareStatement(
                "SELECT COUNT(*) FROM tropicube_notifications WHERE player_uuid = ? AND expires_at > ? AND read_at IS NULL")) {
            count.setString(1, playerId.toString()); count.setLong(2, System.currentTimeMillis());
            try (ResultSet result = count.executeQuery()) { result.next(); unread = result.getInt(1); }
        }
        boolean hasNext = values.size() > size;
        if (hasNext) values.remove(values.size() - 1);
        return new Page(values, page, hasNext, unread);
    }

    private static Action parseAction(String json) {
        if (json == null || json.isBlank()) return Action.none();
        try { return GSON.fromJson(json, Action.class); }
        catch (RuntimeException ignored) { return Action.none(); }
    }

    private static boolean allowedCommand(String value) {
        return value != null && (value.matches("/guild accept [A-Z0-9]{2,8}")
                || value.equals("/missions") || value.equals("/profile")
                || value.matches("/competitive (4v4|8v8)"));
    }
}
