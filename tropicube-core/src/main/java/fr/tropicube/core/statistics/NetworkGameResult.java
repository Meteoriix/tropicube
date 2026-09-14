package fr.tropicube.core.statistics;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Immutable, game-neutral result persisted idempotently by match identifier. */
public record NetworkGameResult(UUID matchId, String gameId, String cause, Set<String> winners,
                                Instant endedAt, List<PlayerResult> players) {
    public NetworkGameResult { winners = Set.copyOf(winners); players = List.copyOf(players); }
    public record PlayerResult(UUID playerId, boolean winner, boolean draw, int eliminations, int deaths, int objectives) { }
}
