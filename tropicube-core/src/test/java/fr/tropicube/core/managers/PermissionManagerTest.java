package fr.tropicube.core.managers;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PermissionManagerTest {
    @Test
    void formatsTheGradeWithoutAppendingAPlayerName() {
        PermissionManager.Grade grade = new PermissionManager.Grade(
                "VIP", "VIP", "<green><bold>VIP</bold></green> ", "", "<green>", 10, 1, 0);

        assertEquals("<green><bold>VIP</bold></green>", PermissionManager.formatGradeDisplay(grade));
    }

    @Test
    void fallsBackToPlayerGradeWhileTheAsyncCacheIsEmpty() {
        PermissionManager.Grade player = new PermissionManager.Grade(
                "JOUEUR", "Joueur", "<gray>Joueur</gray> ", "", "<gray>", 0, 0, 0);
        Map<String, PermissionManager.Grade> registry = Map.of("JOUEUR", player);

        assertEquals(player, PermissionManager.resolveGrade(registry, null));
        assertEquals(player, PermissionManager.resolveGrade(registry, "GRADE_SUPPRIME"));
    }
}
