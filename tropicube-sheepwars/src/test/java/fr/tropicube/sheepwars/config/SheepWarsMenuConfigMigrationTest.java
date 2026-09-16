package fr.tropicube.sheepwars.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SheepWarsMenuConfigMigrationTest {
    @Test
    void migratesLegacyMapMenusAndPreservesOtherValues() throws Exception {
        YamlConfiguration defaults = yaml("""
                menus:
                  map-vote:
                    rows: 6
                    frame: network
                    buttons:
                      previous:
                        slot: 45
                        action: previous_page
                    dynamic-regions:
                      maps:
                        slots: [10, 11, 12]
                        preview-count: 5
                  map-pick:
                    rows: 6
                    frame: network
                    buttons:
                      close:
                        slot: 49
                        action: close
                    dynamic-regions:
                      maps:
                        slots: [10, 11, 12]
                        preview-count: 5
                """);
        YamlConfiguration current = yaml("""
                menus:
                  map-vote:
                    rows: 1
                    frame: neutral
                    buttons: {}
                    dynamic-regions:
                      maps:
                        slots: [0, 1, 2, 3, 4, 5, 6, 7, 8]
                        preview-count: 3
                  map-pick:
                    rows: 1
                    frame: neutral
                    buttons: {}
                    dynamic-regions:
                      maps:
                        slots: [0, 1, 2, 3, 4, 5, 6, 7, 8]
                        preview-count: 4
                """);

        assertTrue(SheepWarsMenuConfigMigration.migrate(current, defaults));
        assertEquals(6, current.getInt("menus.map-vote.rows"));
        assertEquals("network", current.getString("menus.map-vote.frame"));
        assertEquals(45, current.getInt("menus.map-vote.buttons.previous.slot"));
        assertEquals(List.of(10, 11, 12),
                current.getIntegerList("menus.map-vote.dynamic-regions.maps.slots"));
        assertEquals(3, current.getInt("menus.map-vote.dynamic-regions.maps.preview-count"));
        assertEquals(49, current.getInt("menus.map-pick.buttons.close.slot"));
        assertEquals(4, current.getInt("menus.map-pick.dynamic-regions.maps.preview-count"));
        assertFalse(SheepWarsMenuConfigMigration.migrate(current, defaults));
    }

    private static YamlConfiguration yaml(String source) throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(source);
        return yaml;
    }
}
