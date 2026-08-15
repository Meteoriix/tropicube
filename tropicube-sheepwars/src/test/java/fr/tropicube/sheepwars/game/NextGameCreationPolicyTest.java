package fr.tropicube.sheepwars.game;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NextGameCreationPolicyTest {

    @Test
    void onlyMatchmakingInstancesPrepareTheNextMatch() {
        assertTrue(NextGameCreationPolicy.shouldPrepare(new UUID(0, 0)));
        assertFalse(NextGameCreationPolicy.shouldPrepare(UUID.randomUUID()));
    }
}
