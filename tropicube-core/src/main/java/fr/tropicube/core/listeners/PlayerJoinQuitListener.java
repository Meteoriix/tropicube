package fr.tropicube.core.listeners;

import fr.tropicube.core.TropicubeCore;
import org.bukkit.GameRules;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Handles login/logout events.
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

        // Load player data async
        plugin.getPlayerDataManager().loadPlayer(player)
                .thenAccept(profile -> {
                    if (profile == null) return;

                    // Distinguishes a network transfer using the marker placed by Velocity.
                    String transferKey = "transfer:" + uuid;
                    boolean isTransfer = plugin.getRedisManager().exists(transferKey);
                    if (isTransfer) plugin.getRedisManager().delete(transferKey);

                    // Welcome message (private, deleted if transferred)
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        if (!player.isOnline()) {
                            plugin.getPlayerDataManager().unloadPlayer(uuid);
                            return;
                        }
                        long expiry = profile.banExpiry();
                        if (profile.banned() && (expiry <= 0 || expiry > System.currentTimeMillis() / 1000)) {
                            String expStr = expiry <= 0 ? plugin.getLanguageManager().get(uuid, "time.permanent") :
                                    new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm").format(new java.util.Date(expiry * 1000));
                            String reason = profile.banReason() == null
                                    ? plugin.getLanguageManager().get(uuid, "general.no-reason")
                                    : profile.banReason();
                            player.kick(plugin.getLanguageManager().getComponent(
                                    uuid, "moderation.ban-message", reason, expStr));
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

        // Unload data
        plugin.getPlayerDataManager().unloadPlayer(player.getUniqueId());
    }
}
