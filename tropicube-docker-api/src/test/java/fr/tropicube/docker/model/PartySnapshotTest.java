package fr.tropicube.docker.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PartySnapshotTest {
    @Test
    void followersExcludeLeaderAndPreserveIndividualPreference() {
        UUID leader = UUID.randomUUID();
        UUID enabled = UUID.randomUUID();
        UUID disabled = UUID.randomUUID();
        PartySnapshot party = new PartySnapshot("party", leader, List.of(
                new PartyMember(leader, true), new PartyMember(enabled, true), new PartyMember(disabled, false)));

        assertTrue(party.isLeader(leader));
        assertEquals(List.of(enabled, disabled), party.followers().stream().map(PartyMember::playerId).toList());
        assertEquals(List.of(true, false), party.followers().stream().map(PartyMember::followEnabled).toList());
    }

    @Test
    void rejectsSnapshotWithoutLeaderMember() {
        assertThrows(IllegalArgumentException.class, () -> new PartySnapshot(
                "party", UUID.randomUUID(), List.of(new PartyMember(UUID.randomUUID(), true))));
    }
}
