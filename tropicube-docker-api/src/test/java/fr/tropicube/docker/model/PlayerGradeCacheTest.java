package fr.tropicube.docker.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerGradeCacheTest {

    @Test
    void exposesTheSharedRedisKeyAndMatchesGradesCaseInsensitively() {
        UUID uuid = UUID.fromString("12345678-1234-1234-1234-123456789abc");

        assertEquals("player:grade:" + uuid, PlayerGradeCache.key(uuid));
        assertTrue(PlayerGradeCache.isAllowed(" premium ", List.of("PREMIUM", "ADMIN")));
        assertFalse(PlayerGradeCache.isAllowed("VIP", List.of("PREMIUM", "ADMIN")));
        assertFalse(PlayerGradeCache.isAllowed(null, List.of("PREMIUM")));
        assertFalse(PlayerGradeCache.isAllowed("PREMIUM", null));
    }
}
