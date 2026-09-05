package fr.tropicube.sheepwars.powerup;

import org.junit.jupiter.api.Test;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TeamPowerUpSpawnPickerTest {
    @Test
    void includesTheLastCandidateOnFirstPickAndAfterReset() {
        TeamPowerUpSpawnPicker picker = new TeamPowerUpSpawnPicker(new Random() {
            @Override
            public int nextInt(int bound) {
                return bound - 1;
            }
        });

        assertEquals(4, picker.pick(5));
        assertEquals(3, picker.pick(5));
        picker.reset();
        assertEquals(4, picker.pick(5));
        assertEquals(1, picker.pick(2));
    }

    @Test
    void neverRepeatsThePreviousCandidate() {
        TeamPowerUpSpawnPicker picker = new TeamPowerUpSpawnPicker();
        int previous = picker.pick(5);

        for (int attempt = 0; attempt < 100; attempt++) {
            int selected = picker.pick(5);
            assertTrue(selected >= 0 && selected < 5);
            assertNotEquals(previous, selected);
            previous = selected;
        }
    }

    @Test
    void supportsASingleCandidateAndRejectsAnEmptyCatalog() {
        TeamPowerUpSpawnPicker picker = new TeamPowerUpSpawnPicker();

        assertEquals(0, picker.pick(1));
        assertEquals(0, picker.pick(1));
        assertThrows(IllegalArgumentException.class, () -> picker.pick(0));
    }
}
