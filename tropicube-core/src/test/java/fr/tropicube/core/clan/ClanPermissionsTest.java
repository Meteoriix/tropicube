package fr.tropicube.core.clan;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClanPermissionsTest {
    @Test void rolesHaveStrictlyOrderedAdministrativeRights() {
        for (var actor : ClanService.Role.values()) for (var target : ClanService.Role.values()) {
            assertEquals(actor == ClanService.Role.OWNER || actor == ClanService.Role.OFFICER, ClanPermissions.canInvite(actor));
            assertEquals(actor.ordinal() < target.ordinal(), ClanPermissions.canKick(actor, target));
            assertEquals(actor == ClanService.Role.OWNER && target != ClanService.Role.OWNER,
                    ClanPermissions.canManage(actor, target));
        }
    }

    @Test void incompleteContextGrantsNoRights() {
        assertFalse(ClanPermissions.canInvite(null));
        assertFalse(ClanPermissions.canKick(ClanService.Role.OWNER, null));
        assertFalse(ClanPermissions.canManage(ClanService.Role.OWNER, null));
    }

    @Test void resultAndRoleLabelsAreExplicitTranslationKeys() {
        assertEquals("clan.result-not-authorized", ClanPresentation.resultKey(ClanService.Result.NOT_ALLOWED));
        for (var result : ClanService.Result.values()) assertFalse(ClanPresentation.resultKey(result).contains("_"));
        for (var role : ClanService.Role.values()) assertTrue(ClanPresentation.roleKey(role).startsWith("clan.role-"));
        assertEquals("clan.challenge-unknown", ClanPresentation.challengeKey("HISTORIC_UNKNOWN"));
    }
}
