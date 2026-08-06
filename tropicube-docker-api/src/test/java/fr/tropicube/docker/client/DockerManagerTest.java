package fr.tropicube.docker.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class DockerManagerTest {

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
