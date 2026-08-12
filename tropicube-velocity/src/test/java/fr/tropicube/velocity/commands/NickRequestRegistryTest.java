package fr.tropicube.velocity.commands;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NickRequestRegistryTest {

    private static final UUID PLAYER_ID = UUID.fromString("12345678-1234-1234-1234-123456789abc");

    @Test
    void refusesConcurrentGenerationUntilTheActiveRequestCompletes() {
        NickRequestRegistry registry = new NickRequestRegistry();
        Object first = registry.begin(PLAYER_ID);

        assertNotNull(first);
        assertNull(registry.begin(PLAYER_ID));
        assertTrue(registry.complete(PLAYER_ID, first));
        assertNotNull(registry.begin(PLAYER_ID));
    }

    @Test
    void cancellationInvalidatesOnlyThePreviousCallback() {
        NickRequestRegistry registry = new NickRequestRegistry();
        Object cancelled = registry.begin(PLAYER_ID);

        assertTrue(registry.cancel(PLAYER_ID));
        Object replacement = registry.begin(PLAYER_ID);
        assertNotNull(replacement);
        assertFalse(registry.complete(PLAYER_ID, cancelled));
        assertTrue(registry.complete(PLAYER_ID, replacement));
    }
}
