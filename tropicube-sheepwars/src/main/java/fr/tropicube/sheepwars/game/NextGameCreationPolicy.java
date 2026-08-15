package fr.tropicube.sheepwars.game;

import java.util.UUID;

/** Keeps automatic capacity preparation separate from hosted custom matches. */
final class NextGameCreationPolicy {

    private NextGameCreationPolicy() {
    }

    static boolean shouldPrepare(UUID hostUuid) {
        return hostUuid != null && hostUuid.equals(new UUID(0, 0));
    }
}
