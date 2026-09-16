package fr.tropicube.core.progression;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class NetworkProgressionServiceTest {
    @Test void levelCurveUsesStableQuadraticThresholds() {
        assertEquals(1, NetworkProgressionService.levelForExperience(0));
        assertEquals(1, NetworkProgressionService.levelForExperience(99));
        assertEquals(2, NetworkProgressionService.levelForExperience(100));
        assertEquals(3, NetworkProgressionService.levelForExperience(400));
        assertThrows(IllegalArgumentException.class, () -> NetworkProgressionService.levelForExperience(-1));
    }

    @Test void remainingExperienceUsesTheRewardCurveAtBoundaries() {
        assertEquals(100, NetworkProgressionService.experienceToNextLevel(0));
        assertEquals(1, NetworkProgressionService.experienceToNextLevel(99));
        assertEquals(300, NetworkProgressionService.experienceToNextLevel(100));
        assertEquals(1, NetworkProgressionService.experienceToNextLevel(399));
        assertThrows(IllegalArgumentException.class, () -> NetworkProgressionService.experienceToNextLevel(-1));
    }

    @Test void experienceBarTracksProgressWithinTheCurrentLevel() {
        assertEquals(0.0f, NetworkProgressionService.progressWithinLevel(0), 0.0001f);
        assertEquals(0.5f, NetworkProgressionService.progressWithinLevel(50), 0.0001f);
        assertEquals(0.0f, NetworkProgressionService.progressWithinLevel(100), 0.0001f);
        assertEquals(0.5f, NetworkProgressionService.progressWithinLevel(250), 0.0001f);
        assertTrue(NetworkProgressionService.progressWithinLevel(399) < 1.0f);
    }

    @Test void gameDisplayOwnershipDoesNotChangePersistentProgression() {
        NetworkProgressionService service = new NetworkProgressionService(null, null);
        UUID playerId = UUID.randomUUID();
        service.suppressDisplay(playerId);
        assertTrue(service.isDisplaySuppressed(playerId));
        service.releaseDisplay(playerId);
        assertFalse(service.isDisplaySuppressed(playerId));
    }
}
