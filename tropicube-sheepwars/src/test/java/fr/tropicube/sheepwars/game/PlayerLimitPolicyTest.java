package fr.tropicube.sheepwars.game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerLimitPolicyTest {
    @Test
    void clampsConfiguredLimitsToPlayableBounds() {
        assertEquals(2, PlayerLimitPolicy.maximum(1));
        assertEquals(16, PlayerLimitPolicy.maximum(30));
        assertEquals(8, PlayerLimitPolicy.minimum(12, 8));
        assertEquals(2, PlayerLimitPolicy.minimum(1, 16));
    }

    @Test
    void maximumNeverDropsBelowMinimumOrConnectedPlayers() {
        assertEquals(7, PlayerLimitPolicy.decreaseMaximum(8, 2, 3));
        assertEquals(6, PlayerLimitPolicy.decreaseMaximum(6, 2, 6));
        assertEquals(5, PlayerLimitPolicy.decreaseMaximum(5, 5, 2));
        assertEquals(2, PlayerLimitPolicy.decreaseMaximum(2, 2, 1));
    }
}
