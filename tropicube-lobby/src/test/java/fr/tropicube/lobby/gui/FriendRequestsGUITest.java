package fr.tropicube.lobby.gui;

import org.bukkit.event.inventory.ClickType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class FriendRequestsGUITest {

    @Test
    void incomingClicksSelectAcceptOrDeny() {
        FriendRequestsGUI.Action accept = new FriendRequestsGUI.Action(
                FriendRequestsGUI.ActionType.ACCEPT, "Ami");
        FriendRequestsGUI.Action deny = new FriendRequestsGUI.Action(
                FriendRequestsGUI.ActionType.DENY, "Ami");

        assertSame(accept, FriendRequestsGUI.actionForClick(accept, deny, ClickType.LEFT));
        assertSame(deny, FriendRequestsGUI.actionForClick(accept, deny, ClickType.RIGHT));
        assertSame(deny, FriendRequestsGUI.actionForClick(accept, deny, ClickType.SHIFT_RIGHT));
        assertNull(FriendRequestsGUI.actionForClick(accept, deny, ClickType.MIDDLE));
    }

    @Test
    void sentRequestsOnlyReactToRightClick() {
        FriendRequestsGUI.Action cancel = new FriendRequestsGUI.Action(
                FriendRequestsGUI.ActionType.CANCEL, "Ami");

        assertNull(FriendRequestsGUI.actionForClick(null, cancel, ClickType.LEFT));
        assertSame(cancel, FriendRequestsGUI.actionForClick(null, cancel, ClickType.RIGHT));
    }

    @Test
    void pageIsClampedAgainstTheLongestColumn() {
        assertEquals(0, FriendRequestsGUI.normalizedPage(-1, 0, 0));
        assertEquals(0, FriendRequestsGUI.normalizedPage(4, 12, 1));
        assertEquals(1, FriendRequestsGUI.normalizedPage(4, 13, 1));
        assertEquals(2, FriendRequestsGUI.normalizedPage(2, 1, 25));
    }
}
