package fr.tropicube.sheepwars.listener;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtectionListenerTest {

    @Test
    void recognizesEveryRailBlockItem() {
        assertTrue(ProtectionListener.isRailMaterial(Material.RAIL));
        assertTrue(ProtectionListener.isRailMaterial(Material.POWERED_RAIL));
        assertTrue(ProtectionListener.isRailMaterial(Material.DETECTOR_RAIL));
        assertTrue(ProtectionListener.isRailMaterial(Material.ACTIVATOR_RAIL));
    }

    @Test
    void doesNotSuppressUnrelatedItems() {
        assertFalse(ProtectionListener.isRailMaterial(Material.MINECART));
        assertFalse(ProtectionListener.isRailMaterial(Material.STONE));
    }
}
