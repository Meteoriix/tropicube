package fr.tropicube.velocity.managers;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import fr.tropicube.docker.client.RedisManager;
import fr.tropicube.docker.model.PartyMember;
import fr.tropicube.docker.model.PartySnapshot;
import fr.tropicube.docker.model.ServerInstance;
import fr.tropicube.velocity.TropicubeVelocity;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Validates and performs social cross-server transfers. Redis callbacks are kept off
 * Velocity event threads, and every request is revalidated against current proxy state.
 */
public final class PartyCoordinator {
    private static final String FRIEND_JOIN = "PROXY:FRIEND_JOIN:";
    private static final String PARTY_WARP = "PROXY:PARTY_WARP:";

    private final TropicubeVelocity plugin;
    private final ProxyServer proxy;
    private final TropiServerManager servers;
    private final RedisManager redis;
    private final VelocityLanguageManager languages;
    private final Logger logger;
    private final Set<UUID> suppressFollowOnce = ConcurrentHashMap.newKeySet();

    public PartyCoordinator(TropicubeVelocity plugin, TropiServerManager servers, RedisManager redis,
                            VelocityLanguageManager languages, Logger logger) {
        this.plugin = plugin;
        this.proxy = plugin.getServer();
        this.servers = servers;
        this.redis = redis;
        this.languages = languages;
        this.logger = logger;
        redis.subscribeToCommands(this::onCommand);
    }

    private void onCommand(String message) {
        if (message.startsWith(FRIEND_JOIN)) {
            String[] parts = message.substring(FRIEND_JOIN.length()).split(":", 3);
            if (parts.length == 3) runAsync(() -> friendJoin(parts));
        } else if (message.startsWith(PARTY_WARP)) {
            String rawLeader = message.substring(PARTY_WARP.length());
            runAsync(() -> parseUuid(rawLeader, this::warpFollowers));
        }
    }

    private void friendJoin(String[] parts) {
        UUID requesterId = parseUuid(parts[0]);
        UUID targetId = parseUuid(parts[1]);
        if (requesterId == null || targetId == null) return;
        Player requester = proxy.getPlayer(requesterId).orElse(null);
        Player target = proxy.getPlayer(targetId).orElse(null);
        if (requester == null || target == null
                || !parts[2].equals(redis.getPlayerServer(targetId.toString()))) {
            message(requester, "social.friend-join-unavailable");
            return;
        }
        ServerInstance instance = servers.getInstanceById(parts[2]).orElse(null);
        if (!canJoin(instance, requesterId, 1)) {
            message(requester, "social.friend-join-unavailable");
            return;
        }
        suppressFollowOnce.add(requesterId);
        if (!connect(requester, instance)) {
            suppressFollowOnce.remove(requesterId);
            return;
        }
        proxy.getScheduler().buildTask(plugin, () -> suppressFollowOnce.remove(requesterId))
                .delay(10, TimeUnit.SECONDS).schedule();
        message(requester, instance.getStatus() == ServerInstance.Status.GAME_PLAYING
                ? "social.friend-join-spectator" : "social.friend-join-connecting", target.getUsername());
    }

    /** Called after a successful backend switch; a leader automatically brings opted-in members. */
    public void onServerConnected(UUID playerId, String instanceId) {
        if (suppressFollowOnce.remove(playerId)) return;
        runAsync(() -> {
            PartySnapshot party = redis.getParty(playerId);
            if (party != null && party.isLeader(playerId)) warpFollowers(playerId, instanceId, party);
        });
    }

    private void warpFollowers(UUID leaderId) {
        String instanceId = redis.getPlayerServer(leaderId.toString());
        PartySnapshot party = redis.getParty(leaderId);
        if (instanceId != null && party != null && party.isLeader(leaderId)) {
            warpFollowers(leaderId, instanceId, party);
        }
    }

    private void warpFollowers(UUID leaderId, String instanceId, PartySnapshot party) {
        ServerInstance instance = servers.getInstanceById(instanceId).orElse(null);
        Player leader = proxy.getPlayer(leaderId).orElse(null);
        if (instance == null || leader == null) return;

        Set<Player> followers = new HashSet<>();
        for (PartyMember member : party.followers()) {
            if (!member.followEnabled()) continue;
            proxy.getPlayer(member.playerId()).filter(player -> !isOnInstance(player, instanceId)).ifPresent(followers::add);
        }
        if (followers.isEmpty()) return;
        if (!canJoin(instance, followers.stream().map(Player::getUniqueId).toList(), followers.size())) {
            message(leader, "social.party-warp-full");
            return;
        }
        followers.forEach(player -> {
            if (connect(player, instance)) message(player, "social.party-following", leader.getUsername());
        });
    }

    private boolean canJoin(ServerInstance instance, UUID playerId, int incoming) {
        return canJoin(instance, java.util.List.of(playerId), incoming);
    }

    private boolean canJoin(ServerInstance instance, java.util.List<UUID> players, int incoming) {
        if (instance == null || !(instance.getStatus() == ServerInstance.Status.GAME_WAITING
                || instance.getStatus() == ServerInstance.Status.GAME_STARTING
                || instance.getStatus() == ServerInstance.Status.GAME_PLAYING)) return false;
        if (instance.isWhitelisted() && players.stream().anyMatch(id -> !instance.isWhitelistedPlayer(id))) return false;
        int capacity = instance.getStatus() == ServerInstance.Status.GAME_PLAYING
                ? instance.getConnectionCapacity() : instance.getMaxPlayers();
        return instance.getOnlinePlayers() + incoming <= capacity;
    }

    private boolean isOnInstance(Player player, String instanceId) {
        return player.getCurrentServer().flatMap(connection ->
                servers.getInstanceByName(connection.getServerInfo().getName()))
                .map(instance -> instanceId.equals(instance.getInstanceId())).orElse(false);
    }

    private boolean connect(Player player, ServerInstance instance) {
        var server = proxy.getServer(instance.getServerName()).orElse(null);
        if (server == null) {
            message(player, "social.transfer-unavailable");
            return false;
        }
        player.createConnectionRequest(server).fireAndForget();
        return true;
    }

    private void message(Player player, String key, Object... arguments) {
        if (player != null) player.sendMessage(languages.getComponent(player.getUniqueId(), key, arguments));
    }

    private void runAsync(Runnable task) {
        proxy.getScheduler().buildTask(plugin, () -> {
            try { task.run(); }
            catch (RuntimeException exception) { logger.error("Échec d'un transfert social", exception); }
        }).schedule();
    }

    private static void parseUuid(String raw, java.util.function.Consumer<UUID> consumer) {
        UUID value = parseUuid(raw);
        if (value != null) consumer.accept(value);
    }

    private static UUID parseUuid(String raw) {
        try { return UUID.fromString(raw); }
        catch (IllegalArgumentException exception) { return null; }
    }
}
