package fr.tropicube.core.ui;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MenuTemplateRegistryTest {
    @Test
    void removesLegacyClansButtonThatConflictsWithWardrobe() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("""
                menus:
                  profile-home:
                    buttons:
                      clans:
                        slot: 24
                        action: open_clans
                      wardrobe:
                        slot: 24
                        action: open_wardrobe
                """);

        assertTrue(MenuTemplateRegistry.removeLegacyProfileClansButton(yaml));
        assertNull(yaml.getConfigurationSection("menus.profile-home.buttons.clans"));
        assertFalse(MenuTemplateRegistry.removeLegacyProfileClansButton(yaml));
    }

    @Test
    void migratesTheDeployedGameSelectorToANeutralFrame() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("""
                menus:
                  server-type-selector:
                    frame: network
                """);

        assertTrue(MenuTemplateRegistry.useNeutralGameSelectorFrame(yaml));
        assertEquals("neutral", yaml.getString("menus.server-type-selector.frame"));
        assertFalse(MenuTemplateRegistry.useNeutralGameSelectorFrame(yaml));
    }

    @Test
    void exposesTypedStaticButtonsAndDynamicRegions() {
        var button = new MenuTemplateRegistry.Button(11, org.bukkit.Material.LIME_DYE,
                "center.confirm", "center.confirm-action", "confirm", true, 1, false);
        var region = new MenuTemplateRegistry.DynamicRegion(List.of(13, 15), "entry", 2);
        var menu = new MenuTemplateRegistry.Menu("confirm", "center.title", 3, "network",
                Map.of("confirm", button), Map.of("entries", region));

        assertEquals(button, menu.button("confirm"));
        assertEquals(region, menu.dynamicRegion("entries"));
    }
}
