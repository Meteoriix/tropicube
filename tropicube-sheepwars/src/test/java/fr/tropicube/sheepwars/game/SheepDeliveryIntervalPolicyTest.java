package fr.tropicube.sheepwars.game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SheepDeliveryIntervalPolicyTest {

    @Test
    void publicMatchesUseStablePlayerCountTiers() {
        assertEquals(10, SheepDeliveryIntervalPolicy.resolve(false, 15, 2));
        assertEquals(10, SheepDeliveryIntervalPolicy.resolve(false, 15, 4));
        assertEquals(12, SheepDeliveryIntervalPolicy.resolve(false, 15, 5));
        assertEquals(12, SheepDeliveryIntervalPolicy.resolve(false, 15, 8));
        assertEquals(15, SheepDeliveryIntervalPolicy.resolve(false, 15, 9));
        assertEquals(15, SheepDeliveryIntervalPolicy.resolve(false, 15, 16));
    }

    @Test
    void customMatchesKeepTheHostSetting() {
        assertEquals(7, SheepDeliveryIntervalPolicy.resolve(true, 7, 2));
    }
}
