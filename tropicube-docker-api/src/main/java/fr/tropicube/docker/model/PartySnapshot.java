package fr.tropicube.docker.model;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Immutable view of a Redis-backed party. */
public record PartySnapshot(String partyId, UUID leaderId, List<PartyMember> members) {
    public PartySnapshot {
        if (partyId == null || partyId.isBlank()) throw new IllegalArgumentException("partyId est obligatoire");
        Objects.requireNonNull(leaderId, "leaderId");
        members = List.copyOf(Objects.requireNonNull(members, "members"));
        if (members.stream().noneMatch(member -> member.playerId().equals(leaderId))) {
            throw new IllegalArgumentException("Le chef doit appartenir à la party");
        }
    }

    public boolean isLeader(UUID playerId) {
        return leaderId.equals(playerId);
    }

    public List<PartyMember> followers() {
        return members.stream().filter(member -> !member.playerId().equals(leaderId)).toList();
    }
}
