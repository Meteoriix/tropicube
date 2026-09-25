package fr.tropicube.core.managers;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerDataManagerIdentityTest {
    @Test
    void authenticatedNameWinsOverNickedBackendProfile() {
        assertEquals("RealPlayer", PlayerDataManager.canonicalUsername("RealPlayer", "FakeNick"));
    }

    @Test
    void backendNameRemainsACompatibilityFallback() {
        assertEquals("Player", PlayerDataManager.canonicalUsername(null, "Player"));
        assertEquals("Player", PlayerDataManager.canonicalUsername(" ", "Player"));
    }
}
