package fr.tropicube.velocity.managers;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import fr.tropicube.docker.model.ServerInstance;
import org.spongepowered.configurate.ConfigurationNode;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Rotates localized YAML announcements and supports the same targeting rules for staff broadcasts. */
public final class AnnouncementManager {
    public record Entry(String messageKey, String target) {
        public Entry {
            if (messageKey == null || messageKey.isBlank()) throw new IllegalArgumentException("message-key obligatoire");
            if (target == null || target.isBlank()) target = "network";
        }
    }

    private final ProxyServer proxy;
    private final TropiServerManager servers;
    private final VelocityLanguageManager languages;
    private final List<Entry> entries;
    private final AtomicInteger cursor = new AtomicInteger();

    public AnnouncementManager(ProxyServer proxy, TropiServerManager servers,
                               VelocityLanguageManager languages, ConfigurationNode config) {
        this.proxy = proxy;
        this.servers = servers;
        this.languages = languages;
        this.entries = config.node("announcements", "entries").childrenList().stream()
                .map(node -> new Entry(node.node("message-key").getString(""),
                        node.node("target").getString("network")))
                .toList();
    }

    public boolean broadcastNext() {
        if (entries.isEmpty()) return false;
        Entry entry = entries.get(Math.floorMod(cursor.getAndIncrement(), entries.size()));
        broadcastKey(entry.target(), entry.messageKey());
        return true;
    }

    public int broadcastKey(String target, String messageKey) {
        int sent = 0;
        for (Player player : proxy.getAllPlayers()) {
            if (!matches(player, target)) continue;
            player.sendMessage(languages.getComponent(player.getUniqueId(), messageKey));
            sent++;
        }
        return sent;
    }

    private boolean matches(Player player, String target) {
        if ("network".equalsIgnoreCase(target)) return true;
        return player.getCurrentServer()
                .flatMap(connection -> servers.getInstanceByName(connection.getServerInfo().getName()))
                .map(instance -> matches(instance, target))
                .orElse(false);
    }

    private boolean matches(ServerInstance instance, String target) {
        return target.equalsIgnoreCase(instance.getTemplateId())
                || target.equalsIgnoreCase(instance.getServerType())
                || target.equalsIgnoreCase(instance.getInstanceId());
    }
}
