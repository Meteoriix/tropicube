package fr.tropicube.docker.model;

import java.util.Objects;
import java.util.UUID;

/** Shared Redis keys for network-wide secondary authentication sessions. */
public final class SecuritySessionKeys {
    private SecuritySessionKeys() { }

    /** Session created after staff TOTP verification and cleared at proxy disconnect. */
    public static String staff(UUID playerId) {
        return "staff-session:" + Objects.requireNonNull(playerId, "playerId");
    }
}
