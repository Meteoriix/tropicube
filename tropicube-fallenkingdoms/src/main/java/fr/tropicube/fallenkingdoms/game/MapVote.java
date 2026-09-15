package fr.tropicube.fallenkingdoms.game;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Deterministic one-player-one-vote map ballot. */
public final class MapVote {
    private final Map<UUID, String> votes = new LinkedHashMap<>();

    public void vote(UUID playerId, String mapId) { votes.put(playerId, mapId); }
    public String voteOf(UUID playerId) { return votes.get(playerId); }
    public long count(String mapId) { return votes.values().stream().filter(mapId::equals).count(); }
    public String winner(String fallback) {
        return votes.values().stream().distinct()
                .max(Comparator.<String>comparingLong(this::count).thenComparing(Comparator.reverseOrder()))
                .orElse(fallback);
    }
}
