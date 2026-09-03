package fr.tropicube.core.managers;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PermissionManagerTest {
    @Test
    void formatsTheGradeWithoutAppendingAPlayerName() {
        PermissionManager.Grade grade = new PermissionManager.Grade(
                "VIP", "VIP", "<green><bold>VIP</bold></green> ", "", "<green>", 10, 1, 0);

        assertEquals("<green><bold>VIP</bold></green>", PermissionManager.formatGradeDisplay(grade));
    }
}
