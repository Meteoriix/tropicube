package fr.tropicube.velocity.listeners;

import fr.tropicube.velocity.util.MessageStyle;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.permission.PermissionsSetupEvent;
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.permission.Tristate;
import com.velocitypowered.api.proxy.Player;
import fr.tropicube.docker.client.RedisManager;
import fr.tropicube.docker.model.PlayerSessionKeys;
import fr.tropicube.docker.model.SecuritySessionKeys;
import fr.tropicube.docker.model.ServerInstance;
import fr.tropicube.velocity.TropicubeVelocity;
import fr.tropicube.velocity.managers.TropiServerManager;
import fr.tropicube.velocity.managers.AccessProfileCache;
import fr.tropicube.velocity.managers.VelocityLanguageManager;
import org.slf4j.Logger;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Manages player logins/disconnects on the Velocity proxy.
 */
public class PlayerConnectionListener {

    private static final int INITIAL_LOBBY_WELCOME_TTL_SECONDS = 60;

    private final TropicubeVelocity plugin;
    private final TropiServerManager serverManager;
    private final RedisManager redisManager;
    private final Logger logger;
    private final VelocityLanguageManager lm;
    private final AccessProfileCache accessProfiles;

    public PlayerConnectionListener(TropicubeVelocity plugin, TropiServerManager serverManager,
                                    RedisManager redisManager, AccessProfileCache accessProfiles,
                                    Logger logger, VelocityLanguageManager lm) {
        this.plugin = plugin;
        this.serverManager = serverManager;
        this.redisManager = redisManager;
        this.logger = logger;
        this.lm = lm;
        this.accessProfiles = accessProfiles;
    }

    @Subscribe
    public void onPermissionsSetup(PermissionsSetupEvent event) {
        if (!(event.getSubject() instanceof Player player)) return;
        event.setProvider(_ -> permission -> accessProfiles.hasPermission(player.getUniqueId(), permission)
                ? Tristate.TRUE : Tristate.FALSE);
    }

    @Subscribe
    public void onPlayerChooseInitialServer(PlayerChooseInitialServerEvent event) {
        Player player = event.getPlayer();

        // Automatic reconnection to the active game instance the player left.
        String genericRejoinKey = "game:rejoin:" + player.getUniqueId();
        String rejoinInstanceId = redisManager.get(genericRejoinKey);
        if (rejoinInstanceId == null) rejoinInstanceId = redisManager.get("sw:rejoin:" + player.getUniqueId());
        if (rejoinInstanceId != null) {
            String selectedInstance = rejoinInstanceId;
            serverManager.getInstanceById(selectedInstance)
                    .filter(instance -> instance.getStatus() == ServerInstance.Status.GAME_PLAYING)
                    .ifPresent(instance ->
                plugin.getServer().getServer(instance.getServerName()).ifPresent(srv -> {
                    event.setInitialServer(srv);
                    redisManager.delete(genericRejoinKey);
                    redisManager.delete("sw:rejoin:" + player.getUniqueId());
                    logger.debug(MessageStyle.log("PROXY", "<dark_gray>" + "Rejoin auto de {} vers {}"), player.getUsername(), instance.getServerName());
                })
            );
            if (event.getInitialServer().isPresent()) return;
            redisManager.delete(genericRejoinKey);
            redisManager.delete("sw:rejoin:" + player.getUniqueId());
        }

        serverManager.getBestLobby().ifPresentOrElse(lobby -> {
            event.setInitialServer(lobby);
            redisManager.set(PlayerSessionKeys.initialLobbyWelcome(player.getUniqueId()), "1",
                    INITIAL_LOBBY_WELCOME_TTL_SECONDS);
        }, () -> logger.warn(MessageStyle.log("PROXY", "<yellow>Aucun lobby disponible pour {}"),
                event.getPlayer().getUsername()));
    }

    @Subscribe
    public void onLogin(LoginEvent event) {
        if (!event.getResult().isAllowed()) return;
        Player player = event.getPlayer();

        lm.loadPlayerLanguage(player.getUniqueId());
        accessProfiles.refresh(player.getUniqueId());

        // A previous proxy crash may have prevented disconnect cleanup.
        redisManager.delete(SecuritySessionKeys.staff(player.getUniqueId()));
        redisManager.set("player:online:" + player.getUniqueId(), player.getUsername(), 86400);
        redisManager.set("player:uuid:" + player.getUsername().toLowerCase(java.util.Locale.ROOT),
                player.getUniqueId().toString(), 2_592_000);
        redisManager.set("player:name:" + player.getUniqueId(), player.getUsername(), 2_592_000);
        redisManager.publishPlayerEvent("PLAYER_JOIN",
                player.getUniqueId() + ":" + player.getUsername());
        plugin.getPartyCoordinator().onPlayerConnected(player.getUniqueId());

        logger.debug(MessageStyle.log("PROXY", "<dark_gray>" + "Joueur connecté : {}"), player.getUsername());
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        Player player = event.getPlayer();

        // Remembers the server before removal to allow SheepWars reconnection.
        String instanceId = redisManager.getPlayerServer(player.getUniqueId().toString());

        redisManager.delete("player:online:" + player.getUniqueId());
        redisManager.delete(PlayerSessionKeys.initialLobbyWelcome(player.getUniqueId()));
        redisManager.delete(SecuritySessionKeys.staff(player.getUniqueId()));
        redisManager.removePlayerServer(player.getUniqueId().toString());
        plugin.getQueueManager().removeFromQueue(player.getUniqueId());
        serverManager.removeFromMatchmaking(player.getUniqueId());
        lm.unloadPlayer(player.getUniqueId());
        accessProfiles.unload(player.getUniqueId());
        redisManager.publishPlayerEvent("PLAYER_QUIT",
                player.getUniqueId() + ":" + player.getUsername());
        plugin.getPartyCoordinator().onPlayerDisconnected(player.getUniqueId());

        // Active games share one reconnect marker; the legacy SheepWars marker remains readable.
        if (instanceId != null) {
            ServerInstance instance = redisManager.getInstance(instanceId);
            if (instance != null && instance.getStatus() == ServerInstance.Status.GAME_PLAYING
                    && !"LOBBY".equalsIgnoreCase(instance.getServerType())) {
                redisManager.set("game:rejoin:" + player.getUniqueId(), instanceId, 300);
            }
        }
        if (instanceId != null && redisManager.exists("sw:game-started:" + instanceId)) {
            ServerInstance instance = redisManager.getInstance(instanceId);
            if (instance != null && "SHEEPWARS".equalsIgnoreCase(instance.getServerType())) {
                redisManager.set("sw:rejoin:" + player.getUniqueId(), instanceId, 300);
                logger.debug(MessageStyle.log("SHEEPWARS", "<dark_gray>" + "Rejoin stocké pour {} (instance {})"), player.getUsername(), instanceId);
            }
        }

        plugin.getServer().getScheduler().buildTask(plugin, serverManager::refreshPlayerCounts)
                .delay(100, TimeUnit.MILLISECONDS).schedule();

        logger.debug(MessageStyle.log("PROXY", "<dark_gray>" + "Joueur déconnecté : {}"), player.getUsername());
    }

    @Subscribe
    public void onServerPreConnect(ServerPreConnectEvent event) {
        String serverName = event.getOriginalServer().getServerInfo().getName();
        serverManager.getInstanceByName(serverName).ifPresent(instance -> {
            if (instance.isWhitelisted()
                    && !instance.isWhitelistedPlayer(event.getPlayer().getUniqueId())
                    && !event.getPlayer().hasPermission("tropicube.bypass.whitelist")) {
                event.setResult(ServerPreConnectEvent.ServerResult.denied());
                event.getPlayer().sendMessage(lm.getComponent(event.getPlayer().getUniqueId(), "proxy.whitelist"));
            }
        });
    }
}
