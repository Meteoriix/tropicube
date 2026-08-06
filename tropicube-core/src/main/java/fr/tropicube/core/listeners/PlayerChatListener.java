package fr.tropicube.core.listeners;

import fr.tropicube.core.TropicubeCore;
import fr.tropicube.core.managers.PermissionManager;
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
 * Gère le chat avec formatage par grade et filtre anti-spam/mute.
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

        // Vérifier le mute
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

        // Formatage du message
        PermissionManager pm = plugin.getPermissionManager();
        // Une identité active masque le grade réel et présente le grade Premium.
        boolean nicked = plugin.getRedisManager().get("nick:" + uuid) != null;
        PermissionManager.Grade displayGrade = nicked
                ? pm.getAllGrades().getOrDefault("PREMIUM", pm.getGradeInfo(uuid))
                : pm.getGradeInfo(uuid);
        String prefix = displayGrade != null ? displayGrade.prefix() : "";
        String playerColor = displayGrade != null ? displayGrade.color() : "<white>";

        // Récupérer le texte brut du message
        String rawText = PlainTextComponentSerializer.plainText().serialize(event.message());
        Component playerMessage;
        if (player.hasPermission("tropicube.chat.color")) {
            // Les joueurs avec la permission peuvent utiliser les codes &
            playerMessage = LegacyComponentSerializer.legacyAmpersand().deserialize(rawText);
        } else {
            rawText = rawText.replaceAll("&[0-9a-fk-or]", "");
            playerMessage = Component.text(rawText);
        }

        String chatName = PlainTextComponentSerializer.plainText().serialize(player.displayName());
        Component formattedMessage = MiniMessage.miniMessage()
                .deserialize(prefix + playerColor + MiniMessage.miniMessage().escapeTags(chatName) + " <dark_gray>» <white>")
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
