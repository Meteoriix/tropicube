package fr.tropicube.lobby.cosmetic;

import fr.tropicube.core.cosmetic.CosmeticCatalog;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CosmeticRenderingTest {
    @Test void previewExpiresAtFiveSecondsWithoutTouchingEquipment() {
        var entry = new CosmeticCatalog.Entry("breeze", CosmeticCatalog.Category.TRAIL, CosmeticCatalog.Access.FREE, 0, "CLOUD");
        var preview = new PreviewWindow(entry, 200);
        assertTrue(preview.active(100));
        assertTrue(preview.active(199));
        assertFalse(preview.active(200));
        assertFalse(preview.active(205));
        assertSame(entry, preview.entry());
    }
    @Test void hiddenPlayersDisabledEffectsAndOutOfRangeRecipientsReceiveNothing() {
        assertTrue(CosmeticEffects.canReceive(true, true, 24 * 24, 24));
        assertFalse(CosmeticEffects.canReceive(true, true, 24 * 24 + 0.01, 24));
        assertFalse(CosmeticEffects.canReceive(false, true, 0, 24));
        assertFalse(CosmeticEffects.canReceive(true, false, 0, 24));
    }
    @Test void settingsBoundSpatialSearchAndRejectCoercedValues() {
        var config = new YamlConfiguration();
        assertEquals(new CosmeticRenderSettings(5, 2, 100, 24), CosmeticRenderSettings.load(config));
        for (Object invalid : new Object[]{25, 0, 24.5, "24"}) {
            config.set("cosmetics.range-blocks", invalid);
            assertThrows(IllegalArgumentException.class, () -> CosmeticRenderSettings.load(config));
        }
        config.set("cosmetics.range-blocks", 24);
        config.set("cosmetics.particles-per-emission", 21);
        assertThrows(IllegalArgumentException.class, () -> CosmeticRenderSettings.load(config));
    }
}
