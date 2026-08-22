package fr.tropicube.sheepwars.competitive;

import java.time.Duration;

/** Progressive rating window used by both ranked queues. */
public record MatchmakingPolicy(int initialRange, int growthPerStep, Duration step, int maximumRange) {
    public MatchmakingPolicy {
        if (initialRange < 0 || growthPerStep < 0 || step.isZero() || step.isNegative()
                || maximumRange < initialRange) throw new IllegalArgumentException("Fenêtre de matchmaking invalide");
    }

    public int rangeAfter(Duration waiting) {
        if (waiting.isNegative()) throw new IllegalArgumentException("waiting ne peut pas être négatif");
        long steps = waiting.toSeconds() / step.toSeconds();
        return (int) Math.min(maximumRange, initialRange + steps * growthPerStep);
    }
}
