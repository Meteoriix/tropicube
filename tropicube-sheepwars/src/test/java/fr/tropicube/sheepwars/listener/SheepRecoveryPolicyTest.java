package fr.tropicube.sheepwars.listener;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SheepRecoveryPolicyTest {

    private static final UUID OWNER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OPPONENT = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test
    void ownerCannotRecoverOwnSheep() {
        assertFalse(SheepRecoveryPolicy.canRecover(OWNER, OWNER));
    }

    @Test
    void opponentCanRecoverDestroyedSheep() {
        assertTrue(SheepRecoveryPolicy.canRecover(OWNER, OPPONENT));
    }

    @Test
    void missingOwnershipNeverGrantsSheep() {
        assertFalse(SheepRecoveryPolicy.canRecover(null, OPPONENT));
        assertFalse(SheepRecoveryPolicy.canRecover(OWNER, null));
    }
}
