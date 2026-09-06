package fr.tropicube.core.guild;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GuildPermissionsTest {
    @Test void rolesHaveStrictlyOrderedAdministrativeRights() {
        for (var actor : GuildService.Role.values()) for (var target : GuildService.Role.values()) {
            assertEquals(actor == GuildService.Role.OWNER || actor == GuildService.Role.OFFICER, GuildPermissions.canInvite(actor));
            assertEquals(actor.ordinal() < target.ordinal(), GuildPermissions.canKick(actor, target));
            assertEquals(actor == GuildService.Role.OWNER && target != GuildService.Role.OWNER,
                    GuildPermissions.canManage(actor, target));
        }
    }

    @Test void incompleteContextGrantsNoRights() {
        assertFalse(GuildPermissions.canInvite(null));
        assertFalse(GuildPermissions.canKick(GuildService.Role.OWNER, null));
        assertFalse(GuildPermissions.canManage(GuildService.Role.OWNER, null));
    }

    @Test void resultAndRoleLabelsAreExplicitTranslationKeys() {
        assertEquals("guild.result-not-authorized", GuildPresentation.resultKey(GuildService.Result.NOT_ALLOWED));
        for (var result : GuildService.Result.values()) assertFalse(GuildPresentation.resultKey(result).contains("_"));
        for (var role : GuildService.Role.values()) assertTrue(GuildPresentation.roleKey(role).startsWith("guild.role-"));
        assertEquals("guild.challenge-unknown", GuildPresentation.challengeKey("HISTORIC_UNKNOWN"));
    }
}
