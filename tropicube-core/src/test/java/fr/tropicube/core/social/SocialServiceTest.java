package fr.tropicube.core.social;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class SocialServiceTest {

    @Test
    void buildsAllowListedInvitationAcceptanceCommands() {
        SocialService.InvitationAction friend = SocialService.invitationAction(
                "social.friend-request-received", new Object[]{"Player_1"});
        SocialService.InvitationAction party = SocialService.invitationAction(
                "social.party-invite-received", new Object[]{"Leader"});

        assertEquals("/friend accept Player_1", friend.command());
        assertEquals("social.friend-request-accept-hover", friend.hoverKey());
        assertEquals("/party accept Leader", party.command());
        assertEquals("social.party-invite-accept-hover", party.hoverKey());
    }

    @Test
    void rejectsUnknownOrUnsafeNotificationActions() {
        assertNull(SocialService.invitationAction("social.party-member-joined", new Object[]{"Player"}));
        assertNull(SocialService.invitationAction("social.party-invite-received", new Object[0]));
        assertNull(SocialService.invitationAction(
                "social.friend-request-received", new Object[]{"Player /op attacker"}));
    }

    @Test
    void attachesTheAcceptanceCommandToTheAdventureMessage() {
        SocialService.InvitationAction action = new SocialService.InvitationAction(
                "/party accept Leader", "social.party-invite-accept-hover");

        Component clickable = SocialService.clickableInvitation(
                Component.text("Invitation"), Component.text("Accept"), action);

        assertNotNull(clickable.clickEvent());
        assertEquals(ClickEvent.Action.RUN_COMMAND, clickable.clickEvent().action());
        ClickEvent.Payload.Text payload = assertInstanceOf(
                ClickEvent.Payload.Text.class, clickable.clickEvent().payload());
        assertEquals("/party accept Leader", payload.value());
        assertNotNull(clickable.hoverEvent());
    }
}
