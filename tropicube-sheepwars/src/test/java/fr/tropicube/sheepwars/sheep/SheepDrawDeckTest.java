package fr.tropicube.sheepwars.sheep;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class SheepDrawDeckTest {

    @Test
    void avoidsConsecutiveDuplicatesAndPreservesConfiguredWeights() {
        EnumMap<SheepType, Integer> weights = new EnumMap<>(SheepType.class);
        weights.put(SheepType.TNT, 7);
        weights.put(SheepType.DISTORT, 8);
        weights.put(SheepType.METEOR, 5);
        SheepDrawDeck deck = new SheepDrawDeck(weights, new Random(42));
        Map<SheepType, Integer> counts = new EnumMap<>(SheepType.class);

        SheepType previous = null;
        for (int draw = 0; draw < 20_000; draw++) {
            SheepType current = deck.next();
            if (previous != null) assertNotEquals(previous, current, "tirage " + draw);
            counts.merge(current, 1, Integer::sum);
            previous = current;
        }

        assertEquals(7.0 / 20.0, counts.get(SheepType.TNT) / 20_000.0, 0.002);
        assertEquals(8.0 / 20.0, counts.get(SheepType.DISTORT) / 20_000.0, 0.002);
        assertEquals(5.0 / 20.0, counts.get(SheepType.METEOR) / 20_000.0, 0.002);
    }

    @Test
    void supportsAConfigurationWithOnlyOneEnabledType() {
        SheepDrawDeck deck = new SheepDrawDeck(Map.of(SheepType.HEALING, 1), new Random(7));

        for (int draw = 0; draw < 10; draw++) {
            assertEquals(SheepType.HEALING, deck.next());
        }
    }

    @Test
    void preservesAnExtremeWeightEvenWhenConsecutiveDuplicatesAreUnavoidable() {
        SheepDrawDeck deck = new SheepDrawDeck(Map.of(
                SheepType.TNT, 99,
                SheepType.HEALING, 1), new Random(9));
        Map<SheepType, Integer> counts = new EnumMap<>(SheepType.class);

        for (int draw = 0; draw < 100; draw++) {
            counts.merge(deck.next(), 1, Integer::sum);
        }

        assertEquals(99, counts.get(SheepType.TNT));
        assertEquals(1, counts.get(SheepType.HEALING));
    }
}
