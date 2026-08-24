package fr.tropicube.lobby.listeners;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerLobbyListenerTest {
    @Test
    void fullWelcomeIsReservedForInitialLobbyArrival() {
        assertTrue(PlayerLobbyListener.shouldShowWelcomeTitle(true));
        assertFalse(PlayerLobbyListener.shouldShowWelcomeTitle(false));
    }
}
