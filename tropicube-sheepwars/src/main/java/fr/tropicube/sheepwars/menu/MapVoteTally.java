package fr.tropicube.sheepwars.menu;

import fr.tropicube.sheepwars.game.GameMap;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Computes the visible map-vote standing without depending on Bukkit UI state. */
public final class MapVoteTally {
    private MapVoteTally() { }

    public static Standing standing(List<GameMap> maps, Map<UUID, GameMap> votes) {
        Map<GameMap, Integer> counts = counts(maps, votes);
        if (counts.isEmpty()) return new Standing(Status.EMPTY, null, 0);
        int maximum = counts.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        List<GameMap> leaders = counts.entrySet().stream()
                .filter(entry -> entry.getValue() == maximum)
                .map(Map.Entry::getKey)
                .toList();
        return leaders.size() == 1
                ? new Standing(Status.LEADER, leaders.getFirst(), maximum)
                : new Standing(Status.TIE, null, maximum);
    }

    public static Map<GameMap, Integer> counts(List<GameMap> maps, Map<UUID, GameMap> votes) {
        Map<GameMap, Integer> counts = new LinkedHashMap<>();
        maps.forEach(map -> counts.put(map, 0));
        votes.forEach((playerId, map) -> {
            if (counts.containsKey(map)) {
                counts.computeIfPresent(map, (_, count) -> count + 1);
            }
        });
        return counts;
    }

    public enum Status { EMPTY, LEADER, TIE }

    public record Standing(Status status, GameMap leader, int votes) { }
}
