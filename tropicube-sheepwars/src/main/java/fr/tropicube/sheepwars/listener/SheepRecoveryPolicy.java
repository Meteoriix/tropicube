package fr.tropicube.sheepwars.listener;

import java.util.UUID;

/** Pure rule deciding whether a destroyed ability sheep can be recovered. */
final class SheepRecoveryPolicy {

    private SheepRecoveryPolicy() {}

    static boolean canRecover(UUID ownerId, UUID killerId) {
        return ownerId != null && killerId != null && !ownerId.equals(killerId);
    }
}
