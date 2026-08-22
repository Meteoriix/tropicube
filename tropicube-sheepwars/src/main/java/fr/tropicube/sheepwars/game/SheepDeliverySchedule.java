package fr.tropicube.sheepwars.game;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tracks an independent delivery deadline for each player. A missed deadline remains due
 * until the sheep is actually inserted, then the full interval starts again.
 */
final class SheepDeliverySchedule {

    private final int intervalSeconds;
    private final Map<UUID, Integer> nextDeliverySeconds = new HashMap<>();
    private int elapsedSeconds;

    SheepDeliverySchedule(int intervalSeconds, Collection<UUID> playerIds) {
        if (intervalSeconds <= 0) throw new IllegalArgumentException("L'intervalle doit être positif");
        this.intervalSeconds = intervalSeconds;
        playerIds.forEach(playerId -> nextDeliverySeconds.put(playerId, intervalSeconds));
    }

    void advanceSecond() {
        elapsedSeconds = Math.addExact(elapsedSeconds, 1);
    }

    boolean isDue(UUID playerId) {
        Integer deadline = nextDeliverySeconds.get(playerId);
        return deadline != null && elapsedSeconds >= deadline;
    }

    void markDelivered(UUID playerId) {
        markDelivered(playerId, intervalSeconds);
    }

    void markDelivered(UUID playerId, int nextIntervalSeconds) {
        if (nextIntervalSeconds <= 0) throw new IllegalArgumentException("L'intervalle doit être positif");
        if (nextDeliverySeconds.containsKey(playerId)) {
            nextDeliverySeconds.put(playerId, Math.addExact(elapsedSeconds, nextIntervalSeconds));
        }
    }

    int intervalSeconds() { return intervalSeconds; }

    void remove(UUID playerId) {
        nextDeliverySeconds.remove(playerId);
    }
}
