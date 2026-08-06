package fr.tropicube.velocity.managers;

import fr.tropicube.docker.model.ServerInstance;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class InstanceStopAttemptTest {

    @Test
    void restoresPlayableStatusAfterFailedStop() {
        ServerInstance instance = instanceWithStatus(ServerInstance.Status.GAME_WAITING);

        InstanceStopAttempt attempt = InstanceStopAttempt.begin(instance);

        assertNotNull(attempt);
        assertEquals(ServerInstance.Status.STOPPING, instance.getStatus());
        attempt.restore();
        assertEquals(ServerInstance.Status.GAME_WAITING, instance.getStatus());
    }

    @Test
    void rejectsRepeatedOrCompletedStop() {
        assertNull(InstanceStopAttempt.begin(instanceWithStatus(ServerInstance.Status.STOPPING)));
        assertNull(InstanceStopAttempt.begin(instanceWithStatus(ServerInstance.Status.STOPPED)));
    }

    private static ServerInstance instanceWithStatus(ServerInstance.Status status) {
        ServerInstance instance = new ServerInstance(
                UUID.randomUUID().toString(), "sheepwars", "Sheepwars-test", 25_625, false);
        instance.setStatus(status);
        return instance;
    }
}
