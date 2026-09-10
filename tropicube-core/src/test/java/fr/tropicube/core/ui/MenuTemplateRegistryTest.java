package fr.tropicube.core.ui;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MenuTemplateRegistryTest {
    @Test
    void removesLegacyGuildsButtonThatConflictsWithWardrobe() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("""
                menus:
                  profile-home:
                    buttons:
                      guilds:
                        slot: 24
                        action: open_guilds
                      wardrobe:
                        slot: 24
                        action: open_wardrobe
                """);

        assertTrue(MenuTemplateRegistry.removeLegacyProfileGuildsButton(yaml));
        assertNull(yaml.getConfigurationSection("menus.profile-home.buttons.guilds"));
        assertFalse(MenuTemplateRegistry.removeLegacyProfileGuildsButton(yaml));
    }
}
