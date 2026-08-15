package fr.tropicube.sheepwars.sheep;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.random.RandomGenerator;

/** Performs independent weighted draws so every delivery uses the configured probabilities. */
final class SheepWeightedPicker {

    private final EnumMap<SheepType, Integer> weights = new EnumMap<>(SheepType.class);
    private final int totalWeight;

    SheepWeightedPicker(Map<SheepType, Integer> weights) {
        int total = 0;
        for (SheepType type : SheepType.values()) {
            int weight = Math.max(0, weights.getOrDefault(type, 0));
            this.weights.put(type, weight);
            total = Math.addExact(total, weight);
        }
        if (total == 0) {
            throw new IllegalArgumentException("Au moins un type de mouton doit avoir un poids positif");
        }
        this.totalWeight = total;
    }

    SheepType next(RandomGenerator random) {
        Objects.requireNonNull(random, "random");
        int selectedWeight = random.nextInt(totalWeight);
        for (SheepType type : SheepType.values()) {
            selectedWeight -= weights.get(type);
            if (selectedWeight < 0) return type;
        }
        throw new IllegalStateException("Poids de mouton introuvable");
    }
}
