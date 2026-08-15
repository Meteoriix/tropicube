package fr.tropicube.sheepwars.sheep;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.random.RandomGenerator;

/**
 * Applies the weighted distribution independently for each player while preventing a third
 * consecutive occurrence of the same type whenever another type has a positive weight.
 */
final class SheepSequencePicker {

    private static final int MAX_CONSECUTIVE_IDENTICAL = 2;

    private final SheepWeightedPicker weightedPicker;
    private final Map<UUID, DrawHistory> histories = new HashMap<>();

    SheepSequencePicker(Map<SheepType, Integer> weights) {
        weightedPicker = new SheepWeightedPicker(weights);
    }

    SheepType next(UUID playerId, RandomGenerator random) {
        Objects.requireNonNull(playerId, "playerId");
        DrawHistory history = histories.get(playerId);
        SheepType excluded = history != null && history.consecutiveCount() >= MAX_CONSECUTIVE_IDENTICAL
                ? history.type() : null;
        SheepType selected = weightedPicker.next(random, excluded);

        if (history != null && history.type() == selected) {
            histories.put(playerId, new DrawHistory(selected, history.consecutiveCount() + 1));
        } else {
            histories.put(playerId, new DrawHistory(selected, 1));
        }
        return selected;
    }

    void remove(UUID playerId) {
        histories.remove(playerId);
    }

    void clear() {
        histories.clear();
    }

    private record DrawHistory(SheepType type, int consecutiveCount) {}
}
