package fr.tropicube.velocity.managers;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import org.jspecify.annotations.NonNull;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Gère les files d'attente lorsqu'un serveur est plein.
 * Les joueurs VIP sont prioritaires dans la queue.
 */
public class QueueManager {

    private final ProxyServer proxy;
    private final TropiServerManager tropiServerManager;
    private final VelocityLanguageManager lm;

    private final Map<String, Queue<QueueEntry>> queues = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final AtomicLong sequence = new AtomicLong();

    public QueueManager(ProxyServer proxy,
                        TropiServerManager tropiServerManager,
                        VelocityLanguageManager lm) {
        this.proxy = proxy;
        this.tropiServerManager = tropiServerManager;
        this.lm = lm;
        startQueueProcessor();
    }

    public void addToQueue(Player player, String instanceId, boolean vip) {
        Queue<QueueEntry> queue = queues.computeIfAbsent(instanceId, _ -> new PriorityBlockingQueue<>());
        synchronized (queue) {
            if (queue.stream().anyMatch(e -> e.uuid.equals(player.getUniqueId()))) {
                player.sendMessage(lm.getComponent(player.getUniqueId(), "proxy.queue-already"));
                return;
            }
            queue.add(new QueueEntry(player.getUniqueId(), vip, sequence.getAndIncrement()));
        }
        int position = getPosition(queue, player.getUniqueId());
        String key = vip ? "proxy.queue-added-vip" : "proxy.queue-added";
        player.sendMessage(lm.getComponent(player.getUniqueId(), key, position));
    }

    public void removeFromQueue(UUID playerUuid) {
        queues.entrySet().removeIf(entry -> {
            Queue<QueueEntry> queue = entry.getValue();
            queue.removeIf(e -> e.uuid.equals(playerUuid));
            return queue.isEmpty();
        });
    }

    public int getQueuePosition(UUID playerUuid, String instanceId) {
        Queue<QueueEntry> queue = queues.get(instanceId);
        if (queue == null) return -1;
        return getPosition(queue, playerUuid);
    }

    private int getPosition(Queue<QueueEntry> queue, UUID uuid) {
        List<QueueEntry> ordered = new ArrayList<>(queue);
        Collections.sort(ordered);
        for (int i = 0; i < ordered.size(); i++) {
            if (ordered.get(i).uuid.equals(uuid)) return i + 1;
        }
        return -1;
    }

    private void startQueueProcessor() {
        scheduler.scheduleAtFixedRate(() -> queues.forEach((instanceId, queue) -> {
            if (queue.isEmpty()) return;
            var instanceOptional = tropiServerManager.getInstanceById(instanceId);
            if (instanceOptional.isEmpty()) {
                queues.remove(instanceId, queue);
                queue.forEach(entry -> proxy.getPlayer(entry.uuid).ifPresent(player ->
                        player.sendMessage(lm.getComponent(player.getUniqueId(), "proxy.queue-server-unavailable"))));
                queue.clear();
                return;
            }
            var instance = instanceOptional.orElseThrow();
            if (instance.isJoinable()) {
                    var target = proxy.getServer(instance.getServerName());
                    if (target.isEmpty()) return;
                    var targetServer = target.orElseThrow();
                    QueueEntry entry;
                    synchronized (queue) {
                        entry = queue.poll();
                    }
                    if (entry == null) return;
                    proxy.getPlayer(entry.uuid).ifPresent(player ->
                            player.createConnectionRequest(targetServer).connect().whenComplete((result, error) -> {
                                if (error != null || result == null || !result.isSuccessful()) {
                                    if (proxy.getPlayer(entry.uuid).isPresent()) {
                                        queue.offer(entry);
                                        player.sendMessage(lm.getComponent(player.getUniqueId(),
                                                "proxy.queue-transfer-failed"));
                                    }
                                    return;
                                }
                                player.sendMessage(lm.getComponent(player.getUniqueId(),
                                        "proxy.queue-turn", instance.getServerName()));
                            }));
                    List<QueueEntry> ordered = new ArrayList<>(queue);
                    Collections.sort(ordered);
                    for (int i = 0; i < ordered.size(); i++) {
                        int position = i + 1;
                        QueueEntry queued = ordered.get(i);
                        proxy.getPlayer(queued.uuid).ifPresent(p -> p.sendMessage(
                                lm.getComponent(p.getUniqueId(), "proxy.queue-position", position)));
                    }
                    if (queue.isEmpty()) queues.remove(instanceId, queue);
                }
        }), 5, 5, TimeUnit.SECONDS);
    }

    public void shutdown() {
        queues.clear();
        scheduler.shutdownNow();
    }

    private static class QueueEntry implements Comparable<QueueEntry> {
        final UUID uuid;
        final boolean vip;
        final long sequence;

        QueueEntry(UUID uuid, boolean vip, long sequence) {
            this.uuid = uuid;
            this.vip = vip;
            this.sequence = sequence;
        }

        @Override
        public int compareTo(@NonNull QueueEntry other) {
            if (this.vip && !other.vip) return -1;
            if (!this.vip && other.vip) return 1;
            return Long.compare(this.sequence, other.sequence);
        }
    }
}
