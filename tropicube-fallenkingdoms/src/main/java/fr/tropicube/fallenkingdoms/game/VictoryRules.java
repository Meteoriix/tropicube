package fr.tropicube.fallenkingdoms.game;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Pure winner calculation shared by normal and time-limit endings. */
public final class VictoryRules {
    public GameResult lastKingdom(UUID sessionId, Map<KingdomId, Integer> survivors) {
        Set<KingdomId> alive = survivors.entrySet().stream().filter(entry -> entry.getValue() > 0)
                .map(Map.Entry::getKey).collect(Collectors.toUnmodifiableSet());
        return new GameResult(sessionId, alive.size() == 1 ? EndCause.LAST_KINGDOM : EndCause.DRAW,
                alive, survivors, Instant.now());
    }

    public GameResult timeLimit(UUID sessionId, Map<KingdomId, Integer> survivors) {
        int maximum = survivors.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        Set<KingdomId> winners = survivors.entrySet().stream().filter(entry -> entry.getValue() == maximum)
                .map(Map.Entry::getKey).collect(Collectors.toUnmodifiableSet());
        return new GameResult(sessionId, winners.size() > 1 ? EndCause.DRAW : EndCause.TIME_LIMIT,
                winners, survivors, Instant.now());
    }
}
