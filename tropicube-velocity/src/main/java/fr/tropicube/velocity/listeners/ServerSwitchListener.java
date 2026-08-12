package fr.tropicube.velocity.listeners;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import fr.tropicube.docker.client.RedisManager;
import fr.tropicube.velocity.TropicubeVelocity;
import fr.tropicube.velocity.managers.NickManager;
import org.slf4j.Logger;

import java.util.concurrent.TimeUnit;

/**
 * Tracks server changes for Redis.
 * Republished NICK_APPLY each time the server changes if the player is nicknamed,
 * so that the new backend applies the correct skin immediately.
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

        // Reapplies the skin to the new backend to avoid transient display.
        nickManager.getNick(event.getPlayer().getUniqueId()).ifPresent(_ ->
            nickManager.publishNickApply(event.getPlayer().getUniqueId())
        );

        logger.debug("[Tropicube] {} -> {}", event.getPlayer().getUsername(), serverName);
        plugin.getServer().getScheduler().buildTask(plugin,
                plugin.getTropiServerManager()::refreshPlayerCounts)
                .delay(100, TimeUnit.MILLISECONDS).schedule();
    }
}
