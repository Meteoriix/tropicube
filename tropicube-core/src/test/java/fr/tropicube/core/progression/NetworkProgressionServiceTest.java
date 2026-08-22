package fr.tropicube.core.progression;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NetworkProgressionServiceTest {
    @Test void levelCurveUsesStableQuadraticThresholds() {
        assertEquals(1, NetworkProgressionService.levelForExperience(0));
        assertEquals(1, NetworkProgressionService.levelForExperience(99));
        assertEquals(2, NetworkProgressionService.levelForExperience(100));
        assertEquals(3, NetworkProgressionService.levelForExperience(400));
        assertThrows(IllegalArgumentException.class, () -> NetworkProgressionService.levelForExperience(-1));
    }
}
