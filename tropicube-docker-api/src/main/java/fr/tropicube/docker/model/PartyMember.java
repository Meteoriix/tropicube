package fr.tropicube.docker.model;

import java.util.Objects;
import java.util.UUID;

/** Immutable party member state shared by Paper and Velocity. */
public record PartyMember(UUID playerId, boolean followEnabled) {
    public PartyMember {
        Objects.requireNonNull(playerId, "playerId");
    }
}
