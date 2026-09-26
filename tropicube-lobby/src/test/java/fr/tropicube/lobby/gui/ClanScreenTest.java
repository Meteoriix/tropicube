package fr.tropicube.lobby.gui;

import fr.tropicube.core.clan.ClanService;
import org.junit.jupiter.api.Test;
import static fr.tropicube.lobby.gui.ClanScreen.Kind.*;
import static org.junit.jupiter.api.Assertions.*;

class ClanScreenTest {
    @Test void inputDeadlineIsValidatedInsteadOfSilentlyDefaulting() {
        assertEquals(120, ClanScreen.inputTimeout(null));
        assertEquals(10, ClanScreen.inputTimeout(10));
        assertEquals(600, ClanScreen.inputTimeout(600));
        for (Object value : java.util.List.of("120", true, 9, 601, 120.5, Double.NaN))
            assertThrows(IllegalArgumentException.class, () -> ClanScreen.inputTimeout(value));
    }

    @Test void paginatesAllFiftyMembersWithoutAnEmptyLastPage() {
        assertEquals(1, ClanScreen.pageCount(0));
        assertEquals(1, ClanScreen.pageCount(21));
        assertEquals(2, ClanScreen.pageCount(22));
        assertEquals(3, ClanScreen.pageCount(50));
        assertEquals(0, new ClanScreen(ClanScreen.View.MEMBERS, -1, null, null).page());
    }

    @Test void onlyOwnersSeeRoleAndTransferActionsAndNobodyCanTargetThemselves() {
        assertEquals(java.util.List.of(KICK, PROMOTE, TRANSFER), ClanScreen.memberActions(
                ClanService.Role.OWNER, ClanService.Role.MEMBER, false));
        assertEquals(java.util.List.of(KICK, DEMOTE, TRANSFER), ClanScreen.memberActions(
                ClanService.Role.OWNER, ClanService.Role.OFFICER, false));
        assertEquals(java.util.List.of(KICK), ClanScreen.memberActions(
                ClanService.Role.OFFICER, ClanService.Role.MEMBER, false));
        for (var role : ClanService.Role.values()) {
            assertTrue(ClanScreen.memberActions(role, role, true).isEmpty());
            assertTrue(ClanScreen.memberActions(role, ClanService.Role.OWNER, false).isEmpty());
        }
    }
}
