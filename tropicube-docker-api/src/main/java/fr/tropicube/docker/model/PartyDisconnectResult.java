package fr.tropicube.docker.model;

import java.util.UUID;

/** Immutable outcome of an atomic party reconciliation after a member disconnects. */
public record PartyDisconnectResult(Status status, UUID promotedLeaderId) {

    public PartyDisconnectResult {
        if (status == null) throw new IllegalArgumentException("status est obligatoire");
        if ((status == Status.PROMOTED) != (promotedLeaderId != null)) {
            throw new IllegalArgumentException("Un nouveau chef est requis uniquement pour PROMOTED");
        }
    }

    /** Lifecycle action performed by Redis after rechecking the player's presence. */
    public enum Status {
        UNCHANGED,
        REMOVED,
        PROMOTED,
        DISBANDED
    }
}
