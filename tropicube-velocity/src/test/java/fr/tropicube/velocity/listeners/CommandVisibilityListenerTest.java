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
        assertTrue(CommandVisibilityListener.isVisible("2fa"));
        assertTrue(CommandVisibilityListener.isVisible("missions"));
        assertTrue(CommandVisibilityListener.isVisible("quickplay"));
        assertTrue(CommandVisibilityListener.isVisible("competitive"));
        assertTrue(CommandVisibilityListener.isVisible("sheepwars"));
        assertTrue(CommandVisibilityListener.isVisible("maintenance"));
        assertTrue(CommandVisibilityListener.isVisible("networkdiag"));
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
