package fr.tropicube.core.network;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NotificationServiceTest {
    @Test void onlyAllowlistedSuggestedCommandsCanBePersisted() {
        assertDoesNotThrow(() -> new NotificationService.Action(
                NotificationService.ActionType.SUGGEST_COMMAND, "/guild accept TEST"));
        assertDoesNotThrow(() -> new NotificationService.Action(
                NotificationService.ActionType.SUGGEST_COMMAND, "/competitive 8v8"));
        assertThrows(IllegalArgumentException.class, () -> new NotificationService.Action(
                NotificationService.ActionType.SUGGEST_COMMAND, "/op attacker"));
    }
}
