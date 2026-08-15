package fr.tropicube.sheepwars.sheep;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;
import java.util.Random;
import java.util.random.RandomGenerator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SheepWeightedPickerTest {

    @Test
    void mapsEveryWeightIntervalToTheExpectedType() {
        SheepWeightedPicker picker = new SheepWeightedPicker(Map.of(
                SheepType.TNT, 2,
                SheepType.HEALING, 3));

        assertEquals(SheepType.TNT, picker.next(new FixedRandom(0)));
        assertEquals(SheepType.TNT, picker.next(new FixedRandom(1)));
        assertEquals(SheepType.HEALING, picker.next(new FixedRandom(2)));
        assertEquals(SheepType.HEALING, picker.next(new FixedRandom(4)));
    }

    @Test
    void everyDrawUsesTheCompleteDistribution() {
        SheepWeightedPicker picker = new SheepWeightedPicker(Map.of(
                SheepType.TNT, 1,
                SheepType.HEALING, 1));
        FixedRandom firstInterval = new FixedRandom(0);

        assertEquals(SheepType.TNT, picker.next(firstInterval));
        assertEquals(SheepType.TNT, picker.next(firstInterval));
    }

    @Test
    void observedFrequenciesMatchConfiguredProbabilities() {
        Map<SheepType, Integer> weights = Map.ofEntries(
                Map.entry(SheepType.BOARDING, 8), Map.entry(SheepType.TNT, 10),
                Map.entry(SheepType.DISTORT, 3), Map.entry(SheepType.DARKNESS, 6),
                Map.entry(SheepType.SEARCHING, 7), Map.entry(SheepType.FIRE, 8),
                Map.entry(SheepType.POISON, 6), Map.entry(SheepType.SWAP, 7),
                Map.entry(SheepType.METEOR, 4), Map.entry(SheepType.HEALING, 10),
                Map.entry(SheepType.LIGHTNING, 8), Map.entry(SheepType.GRAVITY, 5),
                Map.entry(SheepType.MECHA, 3), Map.entry(SheepType.STRENGTH, 9),
                Map.entry(SheepType.FRAGMENTATION, 6));
        SheepWeightedPicker picker = new SheepWeightedPicker(weights);
        Map<SheepType, Integer> counts = new EnumMap<>(SheepType.class);
        Random random = new Random(42);

        int draws = 1_000_000;
        for (int draw = 0; draw < draws; draw++) {
            counts.merge(picker.next(random), 1, Integer::sum);
        }

        weights.forEach((type, weight) ->
                assertEquals(weight / 100.0, counts.get(type) / (double) draws, 0.001, type.name()));
    }

    @Test
    void ignoresNonPositiveWeightsAndRejectsAnEmptyDistribution() {
        SheepWeightedPicker picker = new SheepWeightedPicker(Map.of(
                SheepType.TNT, 2,
                SheepType.HEALING, 0,
                SheepType.MECHA, -5));

        for (int draw = 0; draw < 100; draw++) {
            assertEquals(SheepType.TNT, picker.next(new Random(draw)));
        }
        assertThrows(IllegalArgumentException.class,
                () -> new SheepWeightedPicker(Map.of()));
    }

    private record FixedRandom(int value) implements RandomGenerator {
        @Override
        public long nextLong() {
            return value;
        }

        @Override
        public int nextInt(int bound) {
            if (value < 0 || value >= bound) throw new IllegalArgumentException("Valeur hors intervalle");
            return value;
        }
    }
}
