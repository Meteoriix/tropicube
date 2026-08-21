package fr.tropicube.velocity.managers;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import fr.tropicube.docker.client.RedisManager;
import fr.tropicube.docker.model.PartyDisconnectResult;
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
import java.util.concurrent.atomic.AtomicBoolean;

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
    private final int disconnectGraceSeconds;
    private final Set<UUID> suppressFollowOnce = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<UUID, ScheduledTask> suppressCleanupTasks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, ScheduledTask> departureTasks = new ConcurrentHashMap<>();
    private final ScheduledTask reconciliationTask;
    private final AtomicBoolean closed = new AtomicBoolean();

    public PartyCoordinator(TropicubeVelocity plugin, TropiServerManager servers, RedisManager redis,
                            VelocityLanguageManager languages, Logger logger, int disconnectGraceSeconds) {
        if (disconnectGraceSeconds <= 0) {
            throw new IllegalArgumentException("party.disconnect-grace-seconds doit être strictement positif");
        }
        this.plugin = plugin;
        this.proxy = plugin.getServer();
        this.servers = servers;
        this.redis = redis;
        this.languages = languages;
        this.logger = logger;
        this.disconnectGraceSeconds = disconnectGraceSeconds;
        redis.subscribeToCommands(this::onCommand);
        this.reconciliationTask = proxy.getScheduler().buildTask(plugin, this::reconcileExpiredDisconnects)
                .repeat(Math.min(10, disconnectGraceSeconds), TimeUnit.SECONDS).schedule();
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
        ScheduledTask cleanup = proxy.getScheduler().buildTask(plugin, () -> {
            suppressFollowOnce.remove(requesterId);
            suppressCleanupTasks.remove(requesterId);
        }).delay(10, TimeUnit.SECONDS).schedule();
        ScheduledTask previousCleanup = suppressCleanupTasks.put(requesterId, cleanup);
        if (previousCleanup != null) previousCleanup.cancel();
        message(requester, instance.getStatus() == ServerInstance.Status.GAME_PLAYING
                ? "social.friend-join-spectator" : "social.friend-join-connecting", target.getUsername());
    }

    /** Called after a successful backend switch; a leader automatically brings opted-in members. */
    public void onServerConnected(UUID playerId, String instanceId) {
        if (suppressFollowOnce.remove(playerId)) {
            ScheduledTask cleanup = suppressCleanupTasks.remove(playerId);
            if (cleanup != null) cleanup.cancel();
            return;
        }
        runAsync(() -> {
            PartySnapshot party = redis.getParty(playerId);
            if (party != null && party.isLeader(playerId)) warpFollowers(playerId, instanceId, party);
        });
    }

    /** Cancels a pending departure and clears its durable marker after a successful proxy login. */
    public void onPlayerConnected(UUID playerId) {
        ScheduledTask pending = departureTasks.remove(playerId);
        if (pending != null) pending.cancel();
        runAsync(() -> redis.clearPartyMemberDisconnected(playerId));
    }

    /** Starts the configured durable party grace period without blocking the disconnect event thread. */
    public void onPlayerDisconnected(UUID playerId) {
        ScheduledTask previous = departureTasks.remove(playerId);
        if (previous != null) previous.cancel();
        ScheduledTask scheduled = proxy.getScheduler().buildTask(plugin, () -> reconcileDisconnect(playerId))
                .delay(disconnectGraceSeconds, TimeUnit.SECONDS).schedule();
        departureTasks.put(playerId, scheduled);
        runAsync(() -> {
            redis.markPartyMemberDisconnected(playerId, nowEpochSecond());
            reconcileDisconnect(playerId, false);
        });
    }

    private void reconcileExpiredDisconnects() {
        if (closed.get()) return;
        try {
            long cutoff = nowEpochSecond() - disconnectGraceSeconds;
            redis.getExpiredPartyDisconnects(cutoff).forEach(this::reconcileDisconnect);
        } catch (RuntimeException exception) {
            logger.error("Échec du balayage des membres de party déconnectés", exception);
        }
    }

    private void reconcileDisconnect(UUID playerId) {
        reconcileDisconnect(playerId, true);
    }

    private void reconcileDisconnect(UUID playerId, boolean cancelScheduledTask) {
        if (closed.get()) return;
        if (cancelScheduledTask) {
            ScheduledTask pending = departureTasks.remove(playerId);
            if (pending != null) pending.cancel();
        }
        try {
            PartyDisconnectResult result = redis.reconcilePartyMemberDisconnect(
                    playerId, nowEpochSecond() - disconnectGraceSeconds);
            if (result.status() == PartyDisconnectResult.Status.PROMOTED) {
                logger.info("Chef de party déconnecté retiré ; nouveau chef : {}", result.promotedLeaderId());
            } else if (result.status() == PartyDisconnectResult.Status.DISBANDED) {
                logger.info("Party supprimée automatiquement car tous ses membres sont hors ligne");
            }
        } catch (RuntimeException exception) {
            logger.error("Échec de la réconciliation party pour " + playerId, exception);
        }
    }

    /** Cancels all owned tasks before Redis is closed. */
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        reconciliationTask.cancel();
        suppressCleanupTasks.values().forEach(ScheduledTask::cancel);
        suppressCleanupTasks.clear();
        departureTasks.values().forEach(ScheduledTask::cancel);
        departureTasks.clear();
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
        if (closed.get()) return;
        proxy.getScheduler().buildTask(plugin, () -> {
            if (closed.get()) return;
            try { task.run(); }
            catch (RuntimeException exception) { logger.error("Échec d'une opération sociale", exception); }
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

    private static long nowEpochSecond() {
        return System.currentTimeMillis() / 1000;
    }
}
