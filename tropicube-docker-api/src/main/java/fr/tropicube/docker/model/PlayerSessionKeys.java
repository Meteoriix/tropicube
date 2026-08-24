package fr.tropicube.docker.model;

import java.util.Objects;
import java.util.UUID;

/** Shared Redis keys whose lifetime is scoped to a player's current proxy session. */
public final class PlayerSessionKeys {
    private PlayerSessionKeys() { }

    /** One-shot marker authorizing the full welcome when the proxy initially routes a player to a lobby. */
    public static String initialLobbyWelcome(UUID playerId) {
        return "session:initial-lobby-welcome:" + Objects.requireNonNull(playerId, "playerId");
    }
}
