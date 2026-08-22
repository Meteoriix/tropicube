package fr.tropicube.core.network;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TotpTest {
    @Test void verifiesRfc6238Sha1VectorAndRejectsReplay() {
        String secret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";
        assertEquals("287082", Totp.generate(secret, 59L / 30));
        long step = 59L / 30;
        assertTrue(Totp.verify(secret, "287082", 59L, step - 1));
        assertFalse(Totp.verify(secret, "287082", 59L, step));
    }
}
