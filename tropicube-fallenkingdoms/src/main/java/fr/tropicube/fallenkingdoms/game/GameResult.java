package fr.tropicube.fallenkingdoms.game;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Immutable terminal result suitable for asynchronous persistence. */
public record GameResult(UUID sessionId, EndCause cause, Set<KingdomId> winners,
                         Map<KingdomId, Integer> survivors, Instant endedAt) {
    public GameResult {
        winners = Set.copyOf(winners);
        survivors = Map.copyOf(survivors);
    }
}
