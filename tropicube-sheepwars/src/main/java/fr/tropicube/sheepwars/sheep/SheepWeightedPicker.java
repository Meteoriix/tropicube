package fr.tropicube.sheepwars.sheep;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.random.RandomGenerator;

/** Performs weighted draws, optionally excluding one type from the current draw. */
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
        return next(random, null);
    }

    SheepType next(RandomGenerator random, SheepType excludedType) {
        Objects.requireNonNull(random, "random");
        int excludedWeight = excludedType == null ? 0 : weights.getOrDefault(excludedType, 0);
        int eligibleWeight = totalWeight - excludedWeight;
        if (eligibleWeight == 0) {
            excludedType = null;
            eligibleWeight = totalWeight;
        }

        int selectedWeight = random.nextInt(eligibleWeight);
        for (SheepType type : SheepType.values()) {
            if (type == excludedType) continue;
            selectedWeight -= weights.get(type);
            if (selectedWeight < 0) return type;
        }
        throw new IllegalStateException("Poids de mouton introuvable");
    }
}
