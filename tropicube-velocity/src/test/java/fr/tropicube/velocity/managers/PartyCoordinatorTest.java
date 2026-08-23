package fr.tropicube.velocity.managers;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PartyCoordinatorTest {

    @Test
    void parsesAnIndividualPartyWarpRequest() {
        UUID leader = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID target = UUID.fromString("00000000-0000-0000-0000-000000000002");

        assertEquals(new PartyCoordinator.WarpMemberRequest(leader, target),
                PartyCoordinator.parseWarpMemberRequest(leader + ":" + target));
    }

    @Test
    void rejectsMalformedOrSelfTargetedPartyWarpRequests() {
        UUID player = UUID.fromString("00000000-0000-0000-0000-000000000001");

        assertNull(PartyCoordinator.parseWarpMemberRequest("invalid"));
        assertNull(PartyCoordinator.parseWarpMemberRequest(player + ":invalid"));
        assertNull(PartyCoordinator.parseWarpMemberRequest(player + ":" + player));
    }
}
