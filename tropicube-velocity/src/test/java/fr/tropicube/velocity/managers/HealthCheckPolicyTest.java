package fr.tropicube.velocity.managers;

import fr.tropicube.docker.model.ServerInstance;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HealthCheckPolicyTest {

    @Test
    void monitorsOnlyReadyGameAndLobbyStates() {
        assertTrue(HealthCheckPolicy.isMonitored(ServerInstance.Status.GAME_WAITING));
        assertTrue(HealthCheckPolicy.isMonitored(ServerInstance.Status.GAME_STARTING));
        assertTrue(HealthCheckPolicy.isMonitored(ServerInstance.Status.GAME_PLAYING));
        assertTrue(HealthCheckPolicy.isMonitored(ServerInstance.Status.GAME_ENDING));
        assertFalse(HealthCheckPolicy.isMonitored(ServerInstance.Status.STARTING));
        assertFalse(HealthCheckPolicy.isMonitored(ServerInstance.Status.STOPPING));
    }

    @Test
    void expiresAfterOneFullTimeout() {
        assertFalse(HealthCheckPolicy.isStale(1_000, 1_059, 60));
        assertTrue(HealthCheckPolicy.isStale(1_000, 1_060, 60));
        assertFalse(HealthCheckPolicy.isStale(1_060, 1_000, 60));
        assertThrows(IllegalArgumentException.class, () -> HealthCheckPolicy.isStale(0, 1, 0));
    }
}
