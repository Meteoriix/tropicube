package fr.tropicube.core.network;

import com.google.gson.Gson;
import fr.tropicube.core.TropicubeCore;
import fr.tropicube.core.managers.DatabaseManager;
import fr.tropicube.docker.model.NetworkChatMessage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/** Global chat, private messages, ignore lists and seven-day offline delivery. */
public final class NetworkCommunicationService {
    private static final Gson GSON = new Gson();
    private static final long OFFLINE_RETENTION_MILLIS = 7L * 24 * 60 * 60 * 1000;
    private static final String CHAT_EVENT = "NETWORK_CHAT:";
    private static final String PRIVATE_EVENT = "PRIVATE_MESSAGE:";

    public enum SendResult { SENT, STORED_OFFLINE, IGNORED, PRIVACY_BLOCKED }
    public record PrivateMessage(UUID senderId, String senderName, UUID recipientId, String body, long sentAt) {}

    private final TropicubeCore plugin;
    private final DatabaseManager database;
    private final PlayerPreferenceService preferences;
    private final Map<UUID, PlayerPreferenceService.Preferences> preferenceCache = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> ignoreCache = new ConcurrentHashMap<>();

    public NetworkCommunicationService(TropicubeCore plugin, DatabaseManager database,
                                       PlayerPreferenceService preferences) {
        this.plugin = plugin;
        this.database = database;
        this.preferences = preferences;
        plugin.getRedisManager().subscribeToPlayerEvents(this::receiveEvent);
    }

    public void playerOnline(UUID playerId) {
        preferences.load(playerId).thenAccept(value -> preferenceCache.put(playerId, value));
        database.supplyAsync(() -> loadIgnored(playerId)).thenAccept(value -> ignoreCache.put(playerId, value));
        deliverOffline(playerId);
    }

    public void playerOffline(UUID playerId) {
        preferenceCache.remove(playerId);
        ignoreCache.remove(playerId);
    }

    public NetworkChatMessage publishGlobal(Player author, String body) {
        String messageId = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String instanceId = System.getenv().getOrDefault("INSTANCE_ID", Bukkit.getServer().getName());
        String visibleName = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(author.displayName());
        NetworkChatMessage message = new NetworkChatMessage(messageId, author.getUniqueId(), visibleName,
                body, instanceId, "GLOBAL", System.currentTimeMillis());
        plugin.getRedisManager().set("chat:recent:" + messageId, message.toJson(), ModerationService.CHAT_BUFFER_SECONDS);
        plugin.getRedisManager().prependCapped("chat:context:" + instanceId, message.toJson(), 7,
                ModerationService.CHAT_BUFFER_SECONDS);
        plugin.getRedisManager().publishPlayerEvent("NETWORK_CHAT", encode(message.toJson()));
        return message;
    }

    public CompletableFuture<SendResult> sendPrivate(UUID senderId, String senderName,
                                                      UUID recipientId, String body) {
        return preferences.load(recipientId).thenCompose(targetPreferences ->
                database.supplyAsync(() -> {
                    if (isIgnored(recipientId, senderId)) return SendResult.IGNORED;
                    if (!privacyAllows(targetPreferences.messagePrivacy(), senderId, recipientId)) {
                        return SendResult.PRIVACY_BLOCKED;
                    }
                    PrivateMessage message = new PrivateMessage(senderId, senderName, recipientId, body,
                            System.currentTimeMillis());
                    plugin.getRedisManager().set("pm:last:" + senderId, recipientId.toString(), 7 * 24 * 60 * 60);
                    plugin.getRedisManager().set("pm:last:" + recipientId, senderId.toString(), 7 * 24 * 60 * 60);
                    if (plugin.getRedisManager().exists("player:online:" + recipientId)) {
                        plugin.getRedisManager().publishPlayerEvent("PRIVATE_MESSAGE", encode(GSON.toJson(message)));
                        return SendResult.SENT;
                    }
                    database.executeUpdate("""
                            INSERT INTO tropicube_private_messages
                                (sender_uuid, recipient_uuid, body, created_at, expires_at, delivered_at)
                            VALUES (?, ?, ?, ?, ?, NULL)
                            """, senderId.toString(), recipientId.toString(), body, message.sentAt(),
                            message.sentAt() + OFFLINE_RETENTION_MILLIS);
                    return SendResult.STORED_OFFLINE;
                }));
    }

    public CompletableFuture<Boolean> toggleIgnore(UUID ownerId, UUID targetId) {
        return database.supplyAsync(() -> {
            if (isIgnored(ownerId, targetId)) {
                database.executeUpdate("DELETE FROM tropicube_ignored_players WHERE owner_uuid = ? AND ignored_uuid = ?",
                        ownerId.toString(), targetId.toString());
                ignoreCache.computeIfAbsent(ownerId, ignored -> ConcurrentHashMap.newKeySet()).remove(targetId);
                return false;
            }
            database.executeUpdate("""
                    INSERT IGNORE INTO tropicube_ignored_players(owner_uuid, ignored_uuid, created_at)
                    VALUES (?, ?, ?)
                    """, ownerId.toString(), targetId.toString(), System.currentTimeMillis());
            ignoreCache.computeIfAbsent(ownerId, ignored -> ConcurrentHashMap.newKeySet()).add(targetId);
            return true;
        });
    }

    public UUID lastConversation(UUID playerId) {
        String value = plugin.getRedisManager().get("pm:last:" + playerId);
        try { return value == null ? null : UUID.fromString(value); }
        catch (IllegalArgumentException ignored) { return null; }
    }

    private boolean privacyAllows(PlayerPreferenceService.MessagePrivacy privacy, UUID sender, UUID recipient) {
        if (privacy == PlayerPreferenceService.MessagePrivacy.EVERYONE) return true;
        if (privacy == PlayerPreferenceService.MessagePrivacy.NOBODY) return false;
        try {
            if (plugin.getRedisManager().getParty(sender) != null
                    && plugin.getRedisManager().getParty(recipient) != null
                    && plugin.getRedisManager().getParty(sender).partyId()
                    .equals(plugin.getRedisManager().getParty(recipient).partyId())) return true;
            return areFriends(sender, recipient);
        } catch (RuntimeException | SQLException error) {
            return false;
        }
    }

    private boolean areFriends(UUID first, UUID second) throws SQLException {
        String a = first.toString().compareTo(second.toString()) < 0 ? first.toString() : second.toString();
        String b = first.toString().compareTo(second.toString()) < 0 ? second.toString() : first.toString();
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT 1 FROM tropicube_friendships
                     WHERE player_a = ? AND player_b = ? AND status = 'ACCEPTED'
                     """)) {
            statement.setString(1, a);
            statement.setString(2, b);
            try (ResultSet result = statement.executeQuery()) { return result.next(); }
        }
    }

    private void receiveEvent(String raw) {
        if (raw.startsWith(CHAT_EVENT)) receiveChat(raw.substring(CHAT_EVENT.length()));
        else if (raw.startsWith(PRIVATE_EVENT)) receivePrivate(raw.substring(PRIVATE_EVENT.length()));
    }

    private void receiveChat(String encoded) {
        NetworkChatMessage message;
        try { message = NetworkChatMessage.fromJson(decode(encoded)); }
        catch (IllegalArgumentException ignored) { return; }
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                PlayerPreferenceService.Preferences value = preferenceCache.getOrDefault(
                        viewer.getUniqueId(), PlayerPreferenceService.Preferences.defaults());
                if (!value.globalChatEnabled() || isIgnored(viewer.getUniqueId(), message.authorId())) continue;
                Component rendered = Component.text("[Global] ", NamedTextColor.AQUA)
                        .append(Component.text(message.authorName(), NamedTextColor.WHITE))
                        .append(Component.text(" > ", NamedTextColor.DARK_GRAY))
                        .append(Component.text(message.body(), NamedTextColor.WHITE));
                if (viewer.hasPermission("tropicube.moderation.chat-evidence")) {
                    rendered = rendered.hoverEvent(HoverEvent.showText(Component.text("Message " + message.messageId()
                                    + " — cliquer pour préparer une sanction")))
                            .clickEvent(ClickEvent.suggestCommand("/mute " + message.authorName()
                                    + " 10m Message inapproprié --evidence " + message.messageId()));
                }
                viewer.sendMessage(rendered);
            }
        });
    }

    private void receivePrivate(String encoded) {
        PrivateMessage message;
        try { message = GSON.fromJson(decode(encoded), PrivateMessage.class); }
        catch (RuntimeException ignored) { return; }
        if (message == null) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player recipient = Bukkit.getPlayer(message.recipientId());
            if (recipient == null || isIgnored(recipient.getUniqueId(), message.senderId())) return;
            recipient.sendMessage(Component.text("[MP] ", NamedTextColor.LIGHT_PURPLE)
                    .append(Component.text(message.senderName() + " > ", NamedTextColor.WHITE))
                    .append(Component.text(message.body(), NamedTextColor.GRAY)));
        });
    }

    private void deliverOffline(UUID recipientId) {
        database.supplyAsync(() -> loadOffline(recipientId)).thenAccept(messages -> {
            if (messages.isEmpty()) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player player = Bukkit.getPlayer(recipientId);
                if (player == null) return;
                for (PrivateMessage message : messages) {
                    player.sendMessage(Component.text("[MP hors ligne] ", NamedTextColor.LIGHT_PURPLE)
                            .append(Component.text(message.senderName() + " > ", NamedTextColor.WHITE))
                            .append(Component.text(message.body(), NamedTextColor.GRAY)));
                }
            });
        });
    }

    private List<PrivateMessage> loadOffline(UUID recipientId) throws SQLException {
        List<PrivateMessage> messages = new ArrayList<>();
        long now = System.currentTimeMillis();
        try (Connection connection = database.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement select = connection.prepareStatement("""
                    SELECT pm.id, pm.sender_uuid, COALESCE(player.username, 'Joueur') sender_name,
                           pm.body, pm.created_at
                    FROM tropicube_private_messages pm
                    LEFT JOIN tropicube_players player ON player.uuid = pm.sender_uuid
                    WHERE pm.recipient_uuid = ? AND pm.delivered_at IS NULL AND pm.expires_at > ?
                    ORDER BY pm.created_at LIMIT 50 FOR UPDATE
                    """)) {
                select.setString(1, recipientId.toString());
                select.setLong(2, now);
                List<Long> ids = new ArrayList<>();
                try (ResultSet result = select.executeQuery()) {
                    while (result.next()) {
                        ids.add(result.getLong("id"));
                        messages.add(new PrivateMessage(UUID.fromString(result.getString("sender_uuid")),
                                result.getString("sender_name"), recipientId, result.getString("body"),
                                result.getLong("created_at")));
                    }
                }
                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE tropicube_private_messages SET delivered_at = ? WHERE id = ?")) {
                    for (Long id : ids) { update.setLong(1, now); update.setLong(2, id); update.addBatch(); }
                    update.executeBatch();
                }
                try (PreparedStatement purge = connection.prepareStatement(
                        "DELETE FROM tropicube_private_messages WHERE expires_at <= ?")) {
                    purge.setLong(1, now);
                    purge.executeUpdate();
                }
                connection.commit();
            } catch (SQLException error) {
                connection.rollback();
                throw error;
            } finally { connection.setAutoCommit(true); }
        }
        return List.copyOf(messages);
    }

    private Set<UUID> loadIgnored(UUID ownerId) throws SQLException {
        Set<UUID> values = ConcurrentHashMap.newKeySet();
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT ignored_uuid FROM tropicube_ignored_players WHERE owner_uuid = ?")) {
            statement.setString(1, ownerId.toString());
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) values.add(UUID.fromString(result.getString(1)));
            }
        }
        return values;
    }

    private boolean isIgnored(UUID owner, UUID target) {
        return ignoreCache.getOrDefault(owner, Set.of()).contains(target);
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }
}
