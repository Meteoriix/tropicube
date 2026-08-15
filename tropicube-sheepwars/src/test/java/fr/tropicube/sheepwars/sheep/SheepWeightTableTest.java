package fr.tropicube.sheepwars.sheep;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SheepWeightTableTest {

    @Test
    void disabledTypesHaveNoProbabilityAndRemainingWeightsAreNormalized() {
        SheepWeightTable table = SheepWeightTable.create(
                Map.of(SheepType.TNT, 10, SheepType.HEALING, 30, SheepType.MECHA, 60),
                EnumSet.of(SheepType.TNT, SheepType.HEALING));

        assertEquals(40, table.total());
        assertEquals(25.0, table.percentage(SheepType.TNT));
        assertEquals(75.0, table.percentage(SheepType.HEALING));
        assertEquals(0.0, table.percentage(SheepType.MECHA));
    }

    @Test
    void allZeroWeightsCreateVisibleFallbackAmongEnabledTypesOnly() {
        SheepWeightTable table = SheepWeightTable.create(Map.of(),
                EnumSet.of(SheepType.DARKNESS, SheepType.HEALING));

        assertEquals(SheepType.DARKNESS, table.fallback());
        assertEquals(1, table.weight(SheepType.DARKNESS));
        assertEquals(100.0, table.percentage(SheepType.DARKNESS));
        assertEquals(0, table.weight(SheepType.TNT));
    }

    @Test
    void refusesAConfigurationWithoutAnyEnabledType() {
        assertThrows(IllegalArgumentException.class,
                () -> SheepWeightTable.create(Map.of(SheepType.TNT, 10), EnumSet.noneOf(SheepType.class)));
    }

    @Test
    void exposedWeightsCannotBeMutated() {
        SheepWeightTable table = SheepWeightTable.create(Map.of(SheepType.TNT, 1),
                EnumSet.of(SheepType.TNT));

        assertThrows(UnsupportedOperationException.class,
                () -> table.weights().put(SheepType.HEALING, 1));
    }

    @Test
    void clampsExternalWeightsToTheSupportedMenuRange() {
        SheepWeightTable table = SheepWeightTable.create(
                Map.of(SheepType.TNT, -5, SheepType.HEALING, Integer.MAX_VALUE),
                EnumSet.of(SheepType.TNT, SheepType.HEALING));

        assertEquals(0, table.weight(SheepType.TNT));
        assertEquals(99, table.weight(SheepType.HEALING));
        assertEquals(99, table.total());
    }
}
