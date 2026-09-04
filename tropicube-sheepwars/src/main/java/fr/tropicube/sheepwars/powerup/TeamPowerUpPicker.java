package fr.tropicube.sheepwars.powerup;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/** Immutable weighted picker used when a wool target appears. */
public final class TeamPowerUpPicker {
    private final List<WeightedType> entries;
    private final long totalWeight;

    public TeamPowerUpPicker(Map<TeamPowerUpType, Integer> weights) {
        List<WeightedType> selected = new ArrayList<>();
        long total = 0;
        for (TeamPowerUpType type : TeamPowerUpType.values()) {
            int weight = weights.getOrDefault(type, 0);
            if (weight < 0) throw new IllegalArgumentException("Poids négatif pour " + type);
            if (weight == 0) continue;
            total = Math.addExact(total, weight);
            selected.add(new WeightedType(type, total));
        }
        if (selected.isEmpty()) throw new IllegalArgumentException("Au moins un bonus d'équipe doit avoir un poids positif");
        this.entries = List.copyOf(selected);
        this.totalWeight = total;
    }

    public TeamPowerUpType pick() {
        return pick(ThreadLocalRandom.current().nextLong(totalWeight));
    }

    /** Deterministic ticket entry point used by tests; tickets range from zero to totalWeight - 1. */
    public TeamPowerUpType pick(long ticket) {
        if (ticket < 0 || ticket >= totalWeight) throw new IllegalArgumentException("Ticket hors intervalle : " + ticket);
        for (WeightedType entry : entries) {
            if (ticket < entry.upperExclusive()) return entry.type();
        }
        throw new IllegalStateException("Table de poids incohérente");
    }

    private record WeightedType(TeamPowerUpType type, long upperExclusive) {}
}
