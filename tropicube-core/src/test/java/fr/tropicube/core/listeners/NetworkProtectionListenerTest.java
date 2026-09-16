package fr.tropicube.core.listeners;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkProtectionListenerTest {
    @Test void gamePolicyMayOpenContainersButNeverProtectedSigns() {
        for (Material material : new Material[]{Material.CHEST, Material.TRAPPED_CHEST, Material.ENDER_CHEST,
                Material.BARREL, Material.FURNACE, Material.BLAST_FURNACE, Material.SMOKER,
                Material.CRAFTING_TABLE}) {
            assertTrue(NetworkProtectionListener.shouldCancel(material, false));
            assertFalse(NetworkProtectionListener.shouldCancel(material, true));
        }
        assertTrue(NetworkProtectionListener.shouldCancel(Material.OAK_SIGN, true));
        assertFalse(NetworkProtectionListener.shouldCancel(Material.BREWING_STAND, false));
    }
}
