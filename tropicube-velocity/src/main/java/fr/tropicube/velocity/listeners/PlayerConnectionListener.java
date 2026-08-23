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
import fr.tropicube.docker.model.SecuritySessionKeys;
import fr.tropicube.docker.model.ServerInstance;
import fr.tropicube.velocity.TropicubeVelocity;
import fr.tropicube.velocity.managers.TropiServerManager;
import fr.tropicube.velocity.managers.VelocityLanguageManager;
import org.slf4j.Logger;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Manages player logins/disconnects on the Velocity proxy.
 */
public class PlayerConnectionListener {

    private static final Set<String> ADMIN_PERMISSIONS = Set.of(
            "tropicube.admin",
            "tropicube.admin.find",
            "tropicube.admin.send",
            "tropicube.admin.pull",
            "tropicube.admin.maintenance",
            "tropicube.admin.announce",
            "tropicube.admin.diagnostic",
            "tropicube.bypass.whitelist"
    );

    private final TropicubeVelocity plugin;
    private final TropiServerManager serverManager;
    private final RedisManager redisManager;
    private final Logger logger;
    private final VelocityLanguageManager lm;

    public PlayerConnectionListener(TropicubeVelocity plugin, TropiServerManager serverManager,
                                    RedisManager redisManager, Logger logger, VelocityLanguageManager lm) {
        this.plugin = plugin;
        this.serverManager = serverManager;
        this.redisManager = redisManager;
        this.logger = logger;
        this.lm = lm;
    }

    @Subscribe
    public void onPermissionsSetup(PermissionsSetupEvent event) {
        if (!(event.getSubject() instanceof Player player)) return;
        try {
            List<String> admins = plugin.getConfig().node("admin-uuids").getList(String.class, List.of());
            boolean isAdmin = admins.stream().map(PlayerConnectionListener::parseUuid)
                    .filter(Objects::nonNull)
                    .anyMatch(player.getUniqueId()::equals);
            if (isAdmin) {
                event.setProvider(_ -> permission -> isAdminPermission(permission)
                        ? Tristate.TRUE
                        : Tristate.UNDEFINED);
            }
        } catch (Exception e) {
            logger.warn(MessageStyle.log("PROXY", "<yellow>Erreur lecture liste admins"), e);
        }
    }

    static boolean isAdminPermission(String permission) {
        return ADMIN_PERMISSIONS.contains(permission);
    }

    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Subscribe
    public void onPlayerChooseInitialServer(PlayerChooseInitialServerEvent event) {
        Player player = event.getPlayer();

        // Automatic reconnection to the left SheepWars game if it still exists.
        String rejoinInstanceId = redisManager.get("sw:rejoin:" + player.getUniqueId());
        if (rejoinInstanceId != null) {
            serverManager.getInstanceById(rejoinInstanceId).ifPresent(instance ->
                plugin.getServer().getServer(instance.getServerName()).ifPresent(srv -> {
                    event.setInitialServer(srv);
                    redisManager.delete("sw:rejoin:" + player.getUniqueId());
                    logger.debug(MessageStyle.log("SHEEPWARS", "<dark_gray>" + "Rejoin auto de {} vers {}"), player.getUsername(), instance.getServerName());
                })
            );
            if (event.getInitialServer().isPresent()) return;
        }

        serverManager.getBestLobby().ifPresentOrElse(
                event::setInitialServer,
                () -> logger.warn(MessageStyle.log("PROXY", "<yellow>Aucun lobby disponible pour {}"), event.getPlayer().getUsername())
        );
    }

    @Subscribe
    public void onLogin(LoginEvent event) {
        if (!event.getResult().isAllowed()) return;
        Player player = event.getPlayer();

        lm.loadPlayerLanguage(player.getUniqueId());

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
        redisManager.delete(SecuritySessionKeys.staff(player.getUniqueId()));
        redisManager.removePlayerServer(player.getUniqueId().toString());
        plugin.getQueueManager().removeFromQueue(player.getUniqueId());
        serverManager.removeFromMatchmaking(player.getUniqueId());
        lm.unloadPlayer(player.getUniqueId());
        redisManager.publishPlayerEvent("PLAYER_QUIT",
                player.getUniqueId() + ":" + player.getUsername());
        plugin.getPartyCoordinator().onPlayerDisconnected(player.getUniqueId());

        // If SheepWars reports an active game in Redis, keeps a game key
        // reconnection so that the player finds the same instance within five minutes.
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
