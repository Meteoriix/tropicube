package fr.tropicube.lobby.gui;

import org.bukkit.event.inventory.ClickType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class PartyInvitesGUITest {

    @Test
    void invitationClicksSelectAcceptOrDeny() {
        PartyInvitesGUI.Action accept = new PartyInvitesGUI.Action(
                PartyInvitesGUI.ActionType.ACCEPT, "Chef");
        PartyInvitesGUI.Action deny = new PartyInvitesGUI.Action(
                PartyInvitesGUI.ActionType.DENY, "Chef");

        assertSame(accept, PartyInvitesGUI.actionForClick(accept, deny, ClickType.LEFT));
        assertSame(deny, PartyInvitesGUI.actionForClick(accept, deny, ClickType.RIGHT));
        assertSame(deny, PartyInvitesGUI.actionForClick(accept, deny, ClickType.SHIFT_RIGHT));
        assertNull(PartyInvitesGUI.actionForClick(accept, deny, ClickType.MIDDLE));
    }

    @Test
    void pageIsClampedToTheInvitationCount() {
        assertEquals(0, PartyInvitesGUI.normalizedPage(-1, 0));
        assertEquals(0, PartyInvitesGUI.normalizedPage(4, 28));
        assertEquals(1, PartyInvitesGUI.normalizedPage(4, 29));
    }
}
