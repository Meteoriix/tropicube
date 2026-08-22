package fr.tropicube.sheepwars.game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReconnectPolicyTest {
    @Test void graceExpiresExactlyAfterThreeMinutes() {
        assertFalse(ReconnectPolicy.expired(1_000L, 180_999L));
        assertTrue(ReconnectPolicy.expired(1_000L, 181_000L));
    }
}
