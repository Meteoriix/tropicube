package fr.tropicube.sheepwars.sheep;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;
import java.util.random.RandomGenerator;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SheepSequencePickerTest {

    @Test
    void preventsAThirdConsecutiveIdenticalSheepForEachPlayer() {
        SheepSequencePicker picker = new SheepSequencePicker(Map.of(
                SheepType.TNT, 2,
                SheepType.HEALING, 1));
        UUID firstPlayer = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID secondPlayer = UUID.fromString("00000000-0000-0000-0000-000000000002");
        FixedRandom firstInterval = new FixedRandom(0);

        assertEquals(SheepType.TNT, picker.next(firstPlayer, firstInterval));
        assertEquals(SheepType.TNT, picker.next(firstPlayer, firstInterval));
        assertEquals(SheepType.HEALING, picker.next(firstPlayer, firstInterval));

        assertEquals(SheepType.TNT, picker.next(secondPlayer, firstInterval));
        assertEquals(SheepType.TNT, picker.next(secondPlayer, firstInterval));
    }

    @Test
    void startsANewSequenceAfterThePlayerHistoryIsRemoved() {
        SheepSequencePicker picker = new SheepSequencePicker(Map.of(
                SheepType.TNT, 1,
                SheepType.HEALING, 1));
        UUID playerId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        FixedRandom firstInterval = new FixedRandom(0);

        picker.next(playerId, firstInterval);
        picker.next(playerId, firstInterval);
        picker.remove(playerId);

        assertEquals(SheepType.TNT, picker.next(playerId, firstInterval));
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
