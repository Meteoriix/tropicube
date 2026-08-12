package fr.tropicube.sheepwars.sheep;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.random.RandomGenerator;

/**
 * Weighted pickaxe specific to a player. The configured tokens are consumed without
 * discount in order to smooth the distribution. As long as the weighting allows it, the
 * next choice preserves the possibility of completing the bag without repetition.
 */
final class SheepDrawDeck {

    private final EnumMap<SheepType, Integer> weights;
    private final RandomGenerator random;
    private final EnumMap<SheepType, Integer> remaining = new EnumMap<>(SheepType.class);
    private int remainingTotal;
    private SheepType previous;

    SheepDrawDeck(Map<SheepType, Integer> weights, RandomGenerator random) {
        this.weights = new EnumMap<>(SheepType.class);
        int total = 0;
        for (SheepType type : SheepType.values()) {
            int weight = Math.max(0, weights.getOrDefault(type, 0));
            this.weights.put(type, weight);
            total += weight;
        }
        if (total == 0) throw new IllegalArgumentException("Au moins un type de mouton doit avoir un poids positif");
        this.random = random;
    }

    SheepType next() {
        if (remainingTotal == 0) refill();

        EnumSet<SheepType> candidates = candidatesThatKeepSequenceFeasible();
        if (candidates.isEmpty()) candidates = candidatesExcludingPrevious();
        if (candidates.isEmpty()) candidates = candidatesWithRemainingTokens();

        SheepType selected = weightedChoice(candidates);
        remaining.compute(selected, (_, count) -> count - 1);
        remainingTotal--;
        previous = selected;
        return selected;
    }

    private void refill() {
        remaining.clear();
        remaining.putAll(weights);
        remainingTotal = weights.values().stream().mapToInt(Integer::intValue).sum();
    }

    private EnumSet<SheepType> candidatesThatKeepSequenceFeasible() {
        EnumSet<SheepType> candidates = EnumSet.noneOf(SheepType.class);
        for (SheepType type : SheepType.values()) {
            if (type != previous && remaining.getOrDefault(type, 0) > 0 && keepsSequenceFeasible(type)) {
                candidates.add(type);
            }
        }
        return candidates;
    }

    private boolean keepsSequenceFeasible(SheepType selected) {
        int totalAfterSelection = remainingTotal - 1;
        for (SheepType type : SheepType.values()) {
            int count = remaining.getOrDefault(type, 0) - (type == selected ? 1 : 0);
            int availableSlots = type == selected ? totalAfterSelection / 2 : (totalAfterSelection + 1) / 2;
            if (count > availableSlots) return false;
        }
        return true;
    }

    private EnumSet<SheepType> candidatesExcludingPrevious() {
        EnumSet<SheepType> candidates = candidatesWithRemainingTokens();
        if (previous != null) candidates.remove(previous);
        return candidates;
    }

    private EnumSet<SheepType> candidatesWithRemainingTokens() {
        EnumSet<SheepType> candidates = EnumSet.noneOf(SheepType.class);
        for (SheepType type : SheepType.values()) {
            if (remaining.getOrDefault(type, 0) > 0) candidates.add(type);
        }
        return candidates;
    }

    private SheepType weightedChoice(EnumSet<SheepType> candidates) {
        int candidateWeight = candidates.stream().mapToInt(remaining::get).sum();
        int selectedWeight = random.nextInt(candidateWeight);
        for (SheepType type : candidates) {
            selectedWeight -= remaining.get(type);
            if (selectedWeight < 0) return type;
        }
        throw new IllegalStateException("Jeton de mouton pondéré introuvable");
    }
}
