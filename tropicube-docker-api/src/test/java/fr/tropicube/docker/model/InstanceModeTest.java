package fr.tropicube.docker.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InstanceModeTest {
    @Test
    void infersLegacyModes() {
        assertEquals(InstanceMode.LOBBY, InstanceMode.infer("lobby", false));
        assertEquals(InstanceMode.QUICK_PLAY, InstanceMode.infer("sheepwars", false));
        assertEquals(InstanceMode.CUSTOM, InstanceMode.infer("sheepwars", true));
        assertTrue(InstanceMode.RANKED_4V4.isRanked());
    }
}
