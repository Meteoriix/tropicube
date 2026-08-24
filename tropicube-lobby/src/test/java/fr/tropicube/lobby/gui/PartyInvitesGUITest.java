package fr.tropicube.lobby.gui;

import org.bukkit.event.inventory.ClickType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class PartyInvitesGUITest {

    @Test
    void invitationClicksSelectAcceptOrDeny() {
        var playerId = java.util.UUID.randomUUID();
        PartyInvitesGUI.Action accept = new PartyInvitesGUI.Action(
                PartyInvitesGUI.ActionType.ACCEPT, playerId, "Chef");
        PartyInvitesGUI.Action deny = new PartyInvitesGUI.Action(
                PartyInvitesGUI.ActionType.DENY, playerId, "Chef");

        assertSame(accept, PartyInvitesGUI.actionForClick(accept, deny, ClickType.LEFT));
        assertSame(deny, PartyInvitesGUI.actionForClick(accept, deny, ClickType.RIGHT));
        assertSame(deny, PartyInvitesGUI.actionForClick(accept, deny, ClickType.SHIFT_RIGHT));
        assertNull(PartyInvitesGUI.actionForClick(accept, deny, ClickType.MIDDLE));
    }

    @Test
    void pageIsClampedToTheLargestInvitationColumn() {
        assertEquals(0, PartyInvitesGUI.normalizedPage(-1, 0, 0));
        assertEquals(0, PartyInvitesGUI.normalizedPage(4, 12, 2));
        assertEquals(1, PartyInvitesGUI.normalizedPage(4, 3, 13));
    }

    @Test
    void sentInvitationUsesRightClickCancellation() {
        var cancel = new PartyInvitesGUI.Action(PartyInvitesGUI.ActionType.CANCEL,
                java.util.UUID.randomUUID(), "Cible");

        assertSame(cancel, PartyInvitesGUI.actionForClick(null, cancel, ClickType.RIGHT));
        assertNull(PartyInvitesGUI.actionForClick(null, cancel, ClickType.LEFT));
    }
}
