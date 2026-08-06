package fr.tropicube.velocity.managers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** File FIFO dédupliquée des joueurs attendant la création d'une instance classique. */
final class MatchmakingWaitlist {

    private final Map<String, LinkedHashSet<UUID>> playersByTemplate = new HashMap<>();

    synchronized boolean add(String templateId, UUID playerId) {
        return playersByTemplate.computeIfAbsent(templateId, _ -> new LinkedHashSet<>()).add(playerId);
    }

    synchronized List<UUID> drain(String templateId, int limit) {
        if (limit <= 0) return List.of();
        LinkedHashSet<UUID> players = playersByTemplate.get(templateId);
        if (players == null || players.isEmpty()) return List.of();

        List<UUID> drained = new ArrayList<>(Math.min(limit, players.size()));
        var iterator = players.iterator();
        while (iterator.hasNext() && drained.size() < limit) {
            drained.add(iterator.next());
            iterator.remove();
        }
        if (players.isEmpty()) playersByTemplate.remove(templateId);
        return List.copyOf(drained);
    }

    synchronized List<UUID> removeAll(String templateId) {
        LinkedHashSet<UUID> removed = playersByTemplate.remove(templateId);
        return removed == null ? List.of() : List.copyOf(removed);
    }

    synchronized boolean hasPlayers(String templateId) {
        LinkedHashSet<UUID> players = playersByTemplate.get(templateId);
        return players != null && !players.isEmpty();
    }

    synchronized void remove(UUID playerId) {
        playersByTemplate.entrySet().removeIf(entry -> {
            entry.getValue().remove(playerId);
            return entry.getValue().isEmpty();
        });
    }

    synchronized void clear() {
        playersByTemplate.clear();
    }
}
