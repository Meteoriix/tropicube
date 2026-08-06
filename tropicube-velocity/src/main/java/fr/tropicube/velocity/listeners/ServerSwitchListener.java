package fr.tropicube.velocity.listeners;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import fr.tropicube.docker.client.RedisManager;
import fr.tropicube.velocity.TropicubeVelocity;
import fr.tropicube.velocity.managers.NickManager;
import org.slf4j.Logger;

import java.util.concurrent.TimeUnit;

/**
 * Suit les changements de serveur pour Redis.
 * Republié NICK_APPLY à chaque changement de serveur si le joueur est nické,
 * afin que le nouveau backend applique le bon skin immédiatement.
 */
public class ServerSwitchListener {

    private final TropicubeVelocity plugin;
    private final RedisManager      redisManager;
    private final NickManager       nickManager;
    private final Logger            logger;

    public ServerSwitchListener(TropicubeVelocity plugin, RedisManager redisManager,
                                NickManager nickManager, Logger logger) {
        this.plugin       = plugin;
        this.redisManager = redisManager;
        this.nickManager  = nickManager;
        this.logger       = logger;
    }

    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        String serverName = event.getServer().getServerInfo().getName();

        plugin.getTropiServerManager().getInstanceByName(serverName).ifPresent(instance -> {
            redisManager.setPlayerServer(event.getPlayer().getUniqueId().toString(), instance.getInstanceId());
            redisManager.publishPlayerEvent("PLAYER_SERVER_SWITCH",
                    event.getPlayer().getUniqueId() + ":" + instance.getInstanceId());
        });

        // Réapplique le skin sur le nouveau backend pour éviter un affichage transitoire.
        nickManager.getNick(event.getPlayer().getUniqueId()).ifPresent(_ ->
            nickManager.publishNickApply(event.getPlayer().getUniqueId())
        );

        logger.debug("[Tropicube] {} -> {}", event.getPlayer().getUsername(), serverName);
        plugin.getServer().getScheduler().buildTask(plugin,
                plugin.getTropiServerManager()::refreshPlayerCounts)
                .delay(100, TimeUnit.MILLISECONDS).schedule();
    }
}
