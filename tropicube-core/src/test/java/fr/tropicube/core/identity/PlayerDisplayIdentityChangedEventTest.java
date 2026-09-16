package fr.tropicube.core.identity;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlayerDisplayIdentityChangedEventTest {

    @Test
    void exposesTheIdentityThatCoreHasFinishedApplying() {
        UUID playerId = UUID.randomUUID();
        PlayerDisplayIdentityChangedEvent event = new PlayerDisplayIdentityChangedEvent(
                playerId, PlayerDisplayIdentityChangedEvent.Change.NICK_REMOVED);

        assertEquals(playerId, event.playerId());
        assertEquals(PlayerDisplayIdentityChangedEvent.Change.NICK_REMOVED, event.change());
    }

    @Test
    void rejectsIncompleteIdentityNotifications() {
        UUID playerId = UUID.randomUUID();

        assertThrows(NullPointerException.class, () -> new PlayerDisplayIdentityChangedEvent(null,
                PlayerDisplayIdentityChangedEvent.Change.NICK_APPLIED));
        assertThrows(NullPointerException.class, () -> new PlayerDisplayIdentityChangedEvent(playerId, null));
    }
}
