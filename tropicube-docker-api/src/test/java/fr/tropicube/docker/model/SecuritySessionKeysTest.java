package fr.tropicube.docker.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SecuritySessionKeysTest {
    @Test
    void buildsTheSharedStaffSessionKey() {
        UUID playerId = UUID.fromString("00000000-0000-0000-0000-000000000001");

        assertEquals("staff-session:" + playerId, SecuritySessionKeys.staff(playerId));
        assertThrows(NullPointerException.class, () -> SecuritySessionKeys.staff(null));
    }
}
