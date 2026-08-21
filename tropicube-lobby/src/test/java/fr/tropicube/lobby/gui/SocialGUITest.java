package fr.tropicube.lobby.gui;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SocialGUITest {

    @Test
    void friendEntriesKeepTheUuidRequiredByPlayerHeads() {
        UUID playerId = UUID.randomUUID();

        SocialGUI.FriendEntry entry = new SocialGUI.FriendEntry(playerId, "Ami", true);

        assertEquals(playerId, entry.playerId());
        assertEquals("Ami", entry.username());
    }
}
