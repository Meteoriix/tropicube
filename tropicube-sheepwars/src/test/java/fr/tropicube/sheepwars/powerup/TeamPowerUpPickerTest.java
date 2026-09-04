package fr.tropicube.sheepwars.powerup;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TeamPowerUpPickerTest {
    @Test
    void respectsEveryWeightedInterval() {
        TeamPowerUpPicker picker = new TeamPowerUpPicker(Map.of(
                TeamPowerUpType.HEALING, 2,
                TeamPowerUpType.POISON_ARROWS, 3,
                TeamPowerUpType.SPEED, 1));

        assertEquals(TeamPowerUpType.HEALING, picker.pick(0));
        assertEquals(TeamPowerUpType.HEALING, picker.pick(1));
        assertEquals(TeamPowerUpType.POISON_ARROWS, picker.pick(2));
        assertEquals(TeamPowerUpType.POISON_ARROWS, picker.pick(4));
        assertEquals(TeamPowerUpType.SPEED, picker.pick(5));
        assertThrows(IllegalArgumentException.class, () -> picker.pick(6));
    }

    @Test
    void ignoresZeroWeightsAndRejectsAnEmptyDistribution() {
        TeamPowerUpPicker picker = new TeamPowerUpPicker(Map.of(
                TeamPowerUpType.HEALING, 0,
                TeamPowerUpType.POISON_ARROWS, 5,
                TeamPowerUpType.SPEED, 0));

        assertEquals(TeamPowerUpType.POISON_ARROWS, picker.pick(0));
        assertEquals(TeamPowerUpType.POISON_ARROWS, picker.pick(4));
        assertThrows(IllegalArgumentException.class, () -> new TeamPowerUpPicker(Map.of()));
    }
}
