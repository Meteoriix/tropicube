package fr.tropicube.fallenkingdoms.game;

import fr.tropicube.fallenkingdoms.config.WorldCycleSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WorldBalanceRulesTest {
    @Test void cycleUsesFiveMinutesPerHalfAndWraps() {
        WorldCycleSettings cycle = new WorldCycleSettings(300, 300);
        assertEquals(0, cycle.timeAt(0));
        assertEquals(11_999, cycle.timeAt(299_999));
        assertEquals(12_000, cycle.timeAt(300_000));
        assertTrue(cycle.isNight(599_999));
        assertEquals(0, cycle.timeAt(600_000));
        assertFalse(cycle.isNight(600_000));
    }

    @Test void spawnRetentionOnlyFiltersNaturalHostilesAtNight() {
        assertFalse(WorldBalanceRules.keepSpawn(true, true, true, true, 0.5, 0.75));
        assertTrue(WorldBalanceRules.keepSpawn(true, false, true, true, 0.5, 0.75));
        assertTrue(WorldBalanceRules.keepSpawn(true, true, false, true, 0.5, 0.75));
        assertTrue(WorldBalanceRules.keepSpawn(true, true, true, false, 0.5, 0.75));
    }

    @Test void flintAndGunpowderRulesPreserveEnchantingProgression() {
        assertEquals(0.25, WorldBalanceRules.flintChance(0.25, 0), 0.0001);
        assertEquals(0.3571, WorldBalanceRules.flintChance(0.25, 1), 0.0001);
        assertEquals(0.625, WorldBalanceRules.flintChance(0.25, 2), 0.0001);
        assertEquals(1.0, WorldBalanceRules.flintChance(0.25, 3), 0.0001);
        assertEquals(4, WorldBalanceRules.multiplyDrop(2, 2.0));
        assertEquals(0, WorldBalanceRules.multiplyDrop(0, 2.0));
    }
}
