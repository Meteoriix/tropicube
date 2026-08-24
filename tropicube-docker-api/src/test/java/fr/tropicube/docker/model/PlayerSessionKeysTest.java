package fr.tropicube.docker.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlayerSessionKeysTest {
    @Test
    void buildsInitialLobbyWelcomeKeyFromPlayerId() {
        UUID playerId = UUID.fromString("2f09b0ab-a6f1-4ddd-bcd6-772f739e7a20");

        assertEquals("session:initial-lobby-welcome:" + playerId,
                PlayerSessionKeys.initialLobbyWelcome(playerId));
    }

    @Test
    void rejectsMissingPlayerId() {
        assertThrows(NullPointerException.class, () -> PlayerSessionKeys.initialLobbyWelcome(null));
    }
}
