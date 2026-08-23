package fr.tropicube.velocity.listeners;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerConnectionListenerTest {
    @Test
    void grantsEveryProxyOperationsPermissionToConfiguredAdmins() {
        assertTrue(PlayerConnectionListener.isAdminPermission("tropicube.admin"));
        assertTrue(PlayerConnectionListener.isAdminPermission("tropicube.admin.find"));
        assertTrue(PlayerConnectionListener.isAdminPermission("tropicube.admin.send"));
        assertTrue(PlayerConnectionListener.isAdminPermission("tropicube.admin.pull"));
        assertTrue(PlayerConnectionListener.isAdminPermission("tropicube.admin.maintenance"));
        assertTrue(PlayerConnectionListener.isAdminPermission("tropicube.admin.announce"));
        assertTrue(PlayerConnectionListener.isAdminPermission("tropicube.admin.diagnostic"));
        assertTrue(PlayerConnectionListener.isAdminPermission("tropicube.bypass.whitelist"));
    }

    @Test
    void leavesUnrelatedPermissionsUndefined() {
        assertFalse(PlayerConnectionListener.isAdminPermission("tropicube.staff"));
        assertFalse(PlayerConnectionListener.isAdminPermission("*"));
    }
}
