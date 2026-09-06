package fr.tropicube.lobby.gui;

import fr.tropicube.core.guild.GuildService;
import org.junit.jupiter.api.Test;
import static fr.tropicube.lobby.gui.GuildScreen.Kind.*;
import static org.junit.jupiter.api.Assertions.*;

class GuildScreenTest {
    @Test void inputDeadlineIsValidatedInsteadOfSilentlyDefaulting() {
        assertEquals(120, GuildScreen.inputTimeout(null));
        assertEquals(10, GuildScreen.inputTimeout(10));
        assertEquals(600, GuildScreen.inputTimeout(600));
        for (Object value : java.util.List.of("120", true, 9, 601, 120.5, Double.NaN))
            assertThrows(IllegalArgumentException.class, () -> GuildScreen.inputTimeout(value));
    }

    @Test void paginatesAllFiftyMembersWithoutAnEmptyLastPage() {
        assertEquals(1, GuildScreen.pageCount(0));
        assertEquals(1, GuildScreen.pageCount(21));
        assertEquals(2, GuildScreen.pageCount(22));
        assertEquals(3, GuildScreen.pageCount(50));
        assertEquals(0, new GuildScreen(GuildScreen.View.MEMBERS, -1, null, null).page());
    }

    @Test void onlyOwnersSeeRoleAndTransferActionsAndNobodyCanTargetThemselves() {
        assertEquals(java.util.List.of(KICK, PROMOTE, TRANSFER), GuildScreen.memberActions(
                GuildService.Role.OWNER, GuildService.Role.MEMBER, false));
        assertEquals(java.util.List.of(KICK, DEMOTE, TRANSFER), GuildScreen.memberActions(
                GuildService.Role.OWNER, GuildService.Role.OFFICER, false));
        assertEquals(java.util.List.of(KICK), GuildScreen.memberActions(
                GuildService.Role.OFFICER, GuildService.Role.MEMBER, false));
        for (var role : GuildService.Role.values()) {
            assertTrue(GuildScreen.memberActions(role, role, true).isEmpty());
            assertTrue(GuildScreen.memberActions(role, GuildService.Role.OWNER, false).isEmpty());
        }
    }
}
