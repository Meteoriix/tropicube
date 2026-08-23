package fr.tropicube.velocity.managers;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatchmakingWaitlistTest {

    @Test
    void deduplicatesPlayersAndPreservesClickOrder() {
        MatchmakingWaitlist waitlist = new MatchmakingWaitlist();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        assertTrue(waitlist.add("sheepwars", first));
        assertFalse(waitlist.add("sheepwars", first));
        assertTrue(waitlist.add("sheepwars", second));

        assertEquals(java.util.List.of(first, second), waitlist.drain("sheepwars", 2));
        assertFalse(waitlist.hasPlayers("sheepwars"));
    }

    @Test
    void keepsOverflowForTheNextInstance() {
        MatchmakingWaitlist waitlist = new MatchmakingWaitlist();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        waitlist.add("sheepwars", first);
        waitlist.add("sheepwars", second);

        assertEquals(java.util.List.of(first), waitlist.drain("sheepwars", 1));
        assertTrue(waitlist.hasPlayers("sheepwars"));
        assertEquals(java.util.List.of(second), waitlist.removeAll("sheepwars"));
        assertFalse(waitlist.hasPlayers("sheepwars"));
    }

    @Test
    void removesDisconnectedPlayerFromEveryTemplate() {
        MatchmakingWaitlist waitlist = new MatchmakingWaitlist();
        UUID player = UUID.randomUUID();
        waitlist.add("sheepwars", player);
        waitlist.add("other-game", player);

        waitlist.remove(player);

        assertFalse(waitlist.hasPlayers("sheepwars"));
        assertFalse(waitlist.hasPlayers("other-game"));
    }

    @Test
    void exposesAndReplacesTheOnlyActiveTemplate() {
        MatchmakingWaitlist waitlist = new MatchmakingWaitlist();
        UUID player = UUID.randomUUID();
        waitlist.add("sheepwars-ranked-4v4", player);

        assertEquals("sheepwars-ranked-4v4", waitlist.templateOf(player));
        waitlist.remove(player);
        waitlist.add("sheepwars-ranked-8v8", player);

        assertEquals("sheepwars-ranked-8v8", waitlist.templateOf(player));
        assertFalse(waitlist.hasPlayers("sheepwars-ranked-4v4"));
    }
}
