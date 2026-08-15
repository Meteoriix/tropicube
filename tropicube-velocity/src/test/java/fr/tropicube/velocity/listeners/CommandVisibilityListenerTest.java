package fr.tropicube.velocity.listeners;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandVisibilityListenerTest {
    @Test
    void exposesInfrastructureCommandsAndAliases() {
        assertTrue(CommandVisibilityListener.isVisible("tropicube"));
        assertTrue(CommandVisibilityListener.isVisible("friend"));
        assertTrue(CommandVisibilityListener.isVisible("GROUPE"));
        assertTrue(CommandVisibilityListener.isVisible("replayconfirm"));
        assertTrue(CommandVisibilityListener.isVisible("balance"));
    }

    @Test
    void hidesVanillaNamespacedAndExternalCommands() {
        assertFalse(CommandVisibilityListener.isVisible("plugins"));
        assertFalse(CommandVisibilityListener.isVisible("version"));
        assertFalse(CommandVisibilityListener.isVisible("minecraft:help"));
        assertFalse(CommandVisibilityListener.isVisible("bukkit:plugins"));
        assertFalse(CommandVisibilityListener.isVisible(null));
    }
}
