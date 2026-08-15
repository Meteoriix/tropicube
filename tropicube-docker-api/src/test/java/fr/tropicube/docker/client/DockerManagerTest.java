package fr.tropicube.docker.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DockerManagerTest {

    @Test
    void buildsStablePerInstanceDataVolumeName() {
        assertEquals("tropicube-data-123e4567-e89b-12d3-a456-426614174000",
                DockerManager.buildDataVolumeName("Tropicube", "123e4567-e89b-12d3-a456-426614174000"));
    }

    @Test
    void rejectsBlankInstanceIdForDataVolume() {
        assertThrows(IllegalArgumentException.class,
                () -> DockerManager.buildDataVolumeName("tropicube", " "));
    }

    @Test
    void rejectsInvalidMinecraftPortRangeBeforeConnecting() {
        assertThrows(IllegalArgumentException.class, () -> new DockerManager(
                "tcp://docker-proxy:2375", "tropicube-net", "tropicube",
                25_700, 25_600, 25_701, 25_800, "", ""));
    }

    @Test
    void rejectsOverlappingMinecraftAndRconRanges() {
        assertThrows(IllegalArgumentException.class, () -> new DockerManager(
                "tcp://docker-proxy:2375", "tropicube-net", "tropicube",
                25_600, 25_700, 25_650, 25_750, "secret", ""));
    }

    @Test
    void rejectsBlankNetworkBeforeConnecting() {
        assertThrows(IllegalArgumentException.class, () -> new DockerManager(
                "tcp://docker-proxy:2375", " ", "tropicube",
                25_600, 25_700, 25_701, 25_800, "", ""));
    }
}
