package fr.tropicube.core.listeners;

import fr.tropicube.core.TropicubeCore;
import fr.tropicube.core.managers.PermissionManager;
import fr.tropicube.docker.model.NickIdentity;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.UUID;

/**
 * Manages chat with grade formatting and spam/mute filter.
 */
public class PlayerChatListener implements Listener {

    private final TropicubeCore plugin;
    private final java.util.Map<UUID, Long> lastMessage = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long CHAT_COOLDOWN_MS = 1500;

    public PlayerChatListener(TropicubeCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onAsyncChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Check mute
        if (plugin.getPlayerDataManager().isMuted(uuid)) {
            event.setCancelled(true);
            long expiry = plugin.getPlayerDataManager().getMuteExpiry(uuid);
            String timeLeft = expiry == -1 ? "Permanent" : formatTimeLeft(expiry);
            player.sendMessage(plugin.getLanguageManager().getComponent(uuid, "moderation.muted", timeLeft));
            return;
        }

        // Anti-spam
        long now = System.currentTimeMillis();
        Long last = lastMessage.get(uuid);
        if (last != null && (now - last) < CHAT_COOLDOWN_MS && !player.hasPermission("tropicube.bypass.spam")) {
            event.setCancelled(true);
            player.sendMessage(plugin.getLanguageManager().getComponent(uuid, "general.chat-cooldown"));
            return;
        }
        lastMessage.put(uuid, now);

        // Format the message
        PermissionManager pm = plugin.getPermissionManager();
        // An active identity carries its persistent display grade in Redis.
        String nickPayload = plugin.getRedisManager().get(NickIdentity.key(uuid));
        String displayGradeName = NickIdentity.fromJson(nickPayload)
                .map(NickIdentity::displayGrade)
                .orElse(null);
        PermissionManager.Grade displayGrade = displayGradeName == null
                ? pm.getGradeInfo(uuid)
                : pm.getAllGrades().getOrDefault(displayGradeName, pm.getGradeInfo(uuid));
        String prefix = displayGrade != null ? displayGrade.prefix() : "";
        String playerColor = displayGrade != null ? displayGrade.color() : "<white>";

        // Retrieve the raw text of the message
        String rawText = PlainTextComponentSerializer.plainText().serialize(event.message());
        Component playerMessage;
        if (player.hasPermission("tropicube.chat.color")) {
            // Players with permission can use codes &
            playerMessage = LegacyComponentSerializer.legacyAmpersand().deserialize(rawText);
        } else {
            rawText = rawText.replaceAll("&[0-9a-fk-or]", "");
            playerMessage = Component.text(rawText);
        }

        String chatName = PlainTextComponentSerializer.plainText().serialize(player.displayName());
        Component formattedMessage = MiniMessage.miniMessage()
                .deserialize(prefix + playerColor + MiniMessage.miniMessage().escapeTags(chatName) + " <dark_gray>> <white>")
                .append(playerMessage);
        event.renderer((source, sourceDisplayName, message, viewer) -> formattedMessage);
    }

    private String formatTimeLeft(long expiryEpoch) {
        long seconds = expiryEpoch - (System.currentTimeMillis() / 1000);
        if (seconds <= 0) return "0s";
        if (seconds < 60) return seconds + "s";
        if (seconds < 3600) return (seconds / 60) + "m";
        return (seconds / 3600) + "h";
    }
}
