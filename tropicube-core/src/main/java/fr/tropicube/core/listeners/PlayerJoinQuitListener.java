package fr.tropicube.core.listeners;

import fr.tropicube.core.TropicubeCore;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.GameRules;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Gère les événements de connexion/déconnexion.
 */
public class PlayerJoinQuitListener implements Listener {

    private final TropicubeCore plugin;

    public PlayerJoinQuitListener(TropicubeCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        event.joinMessage(null); // L'événement ne doit pas être modifié après sa clôture.

        // Charger les données du joueur de façon async
        plugin.getPlayerDataManager().loadPlayer(player)
                .thenAccept(profile -> {
                    if (profile == null) return;

                    // Distingue un transfert réseau grâce au marqueur posé par Velocity.
                    String transferKey = "transfer:" + uuid;
                    boolean isTransfer = plugin.getRedisManager().exists(transferKey);
                    if (isTransfer) plugin.getRedisManager().delete(transferKey);

                    // Message de bienvenue (privé, supprimé en cas de transfert)
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        if (!player.isOnline()) {
                            plugin.getPlayerDataManager().unloadPlayer(uuid);
                            return;
                        }
                        long expiry = profile.banExpiry();
                        if (profile.banned() && (expiry <= 0 || expiry > System.currentTimeMillis() / 1000)) {
                            String expStr = expiry <= 0 ? "Permanent" :
                                    new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm").format(new java.util.Date(expiry * 1000));
                            String reason = profile.banReason() == null ? "Non précisée" : profile.banReason();
                            player.kick(MiniMessage.miniMessage().deserialize(
                                    "<red>Vous êtes banni.\n<gray>Raison : <white><reason>\n<gray>Expiration : <white><expiry>",
                                    Placeholder.unparsed("reason", reason),
                                    Placeholder.unparsed("expiry", expStr)));
                            return;
                        }
                        if (!isTransfer) {
                            boolean firstJoin = profile.firstJoin() == profile.lastJoin();
                            String welcomeKey = firstJoin ? "join.first-join" : "join.welcome-back";
                            player.sendMessage(plugin.getLanguageManager().getComponent(uuid, welcomeKey, player.getName()));
                        }
                    });
                });
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        event.getWorld().setGameRule(GameRules.LOCATOR_BAR, false);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        event.quitMessage(null);

        // Décharger les données
        plugin.getPlayerDataManager().unloadPlayer(player.getUniqueId());
    }
}
