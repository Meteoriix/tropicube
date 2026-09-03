package fr.tropicube.core.ui;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TablistTemplateTest {

    @Test
    void parsesHeaderAndFooterKeysForEveryVariant() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("version", 1);
        yaml.set("tablists.lobby.variants.default.header-key", "lobby.tab-header");
        yaml.set("tablists.lobby.variants.default.footer-key", "lobby.tab-footer");

        TablistTemplate template = TablistTemplate.parse(yaml, "tablists.yml", "lobby");

        assertEquals("lobby.tab-header", template.variant("default").headerKey());
        assertEquals("lobby.tab-footer", template.variant("default").footerKey());
        assertThrows(IllegalArgumentException.class, () -> template.variant("missing"));
    }

    @Test
    void rejectsAnIncompleteVariant() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("version", 1);
        yaml.set("tablists.lobby.variants.default.header-key", "lobby.tab-header");

        assertThrows(IllegalArgumentException.class,
                () -> TablistTemplate.parse(yaml, "tablists.yml", "lobby"));
    }
}
