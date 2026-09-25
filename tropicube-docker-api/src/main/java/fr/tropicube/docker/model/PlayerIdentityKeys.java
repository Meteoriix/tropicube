package fr.tropicube.docker.model;

import java.util.Objects;
import java.util.UUID;

/** Shared Redis keys for canonical player identity data supplied by the authenticated proxy. */
public final class PlayerIdentityKeys {
    private PlayerIdentityKeys() { }

    /** Authenticated Mojang/Floodgate name, independent from an active nick. TTL: 30 days. */
    public static String authenticatedName(UUID playerId) {
        return "player:authenticated-name:" + Objects.requireNonNull(playerId, "playerId");
    }
}
