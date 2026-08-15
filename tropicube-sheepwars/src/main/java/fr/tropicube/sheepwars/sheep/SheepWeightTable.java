package fr.tropicube.sheepwars.sheep;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** Immutable, validated runtime distribution of enabled sheep types. */
final class SheepWeightTable {

    private final Map<SheepType, Integer> weights;
    private final int total;
    private final SheepType fallback;

    private SheepWeightTable(EnumMap<SheepType, Integer> weights, int total, SheepType fallback) {
        this.weights = Collections.unmodifiableMap(weights);
        this.total = total;
        this.fallback = fallback;
    }

    /**
     * Builds the effective table. Disabled types always receive zero weight. If every enabled
     * weight is zero, the first enabled catalog entry becomes an explicit one-token fallback.
     */
    static SheepWeightTable create(Map<SheepType, Integer> configuredWeights,
                                   Set<SheepType> enabledTypes) {
        EnumSet<SheepType> enabled = enabledTypes.isEmpty()
                ? EnumSet.noneOf(SheepType.class) : EnumSet.copyOf(enabledTypes);
        if (enabled.isEmpty()) {
            throw new IllegalArgumentException("Au moins un type de mouton doit être activé");
        }

        EnumMap<SheepType, Integer> effective = new EnumMap<>(SheepType.class);
        int total = 0;
        for (SheepType type : SheepType.values()) {
            int weight = enabled.contains(type)
                    ? Math.clamp(configuredWeights.getOrDefault(type, 0), 0, 99) : 0;
            effective.put(type, weight);
            total = Math.addExact(total, weight);
        }

        SheepType fallback = null;
        if (total == 0) {
            fallback = enabled.iterator().next();
            effective.put(fallback, 1);
            total = 1;
        }
        return new SheepWeightTable(effective, total, fallback);
    }

    Map<SheepType, Integer> weights() {
        return weights;
    }

    int total() {
        return total;
    }

    int weight(SheepType type) {
        return weights.getOrDefault(type, 0);
    }

    double percentage(SheepType type) {
        return weight(type) * 100.0 / total;
    }

    SheepType fallback() {
        return fallback;
    }
}
