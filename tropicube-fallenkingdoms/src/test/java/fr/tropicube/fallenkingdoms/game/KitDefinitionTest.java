package fr.tropicube.fallenkingdoms.game;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class KitDefinitionTest {
    @Test
    void aggregatesOnlyIdenticalConfiguredItems() {
        var experience = new KitDefinition.KitItem(Material.EXPERIENCE_BOTTLE, 64, 0, null, 0, Map.of());
        var experienceSecondStack = new KitDefinition.KitItem(Material.EXPERIENCE_BOTTLE, 64, 1, null, 0, Map.of());
        var speed = new KitDefinition.KitItem(Material.POTION, 1, 2, "SWIFTNESS", 0, Map.of());
        var longSpeed = new KitDefinition.KitItem(Material.POTION, 1, 3, "LONG_SWIFTNESS", 0, Map.of());
        var sharpness = new KitDefinition.KitItem(Material.ENCHANTED_BOOK, 1, 4, null, 0, Map.of("sharpness", 2));

        List<KitDefinition.KitItemSummary> summaries = new KitDefinition("test", Material.CHEST,
                List.of(experience, experienceSecondStack, speed, longSpeed, sharpness)).contentSummary();

        assertEquals(4, summaries.size());
        assertEquals(128, summaries.getFirst().amount());
        assertEquals(2, summaries.getFirst().stacks());
        assertEquals("SWIFTNESS", summaries.get(1).item().potionType());
        assertEquals("LONG_SWIFTNESS", summaries.get(2).item().potionType());
        assertEquals(Map.of("sharpness", 2), summaries.get(3).item().enchantments());
    }

    @Test
    void everyBundledKitContentHasLocalizedPresentation() {
        var config = YamlConfiguration.loadConfiguration(Path.of("src/main/resources/config.yml").toFile());
        var definitions = KitCatalog.load(config).definitions().values();
        for (String language : List.of("fr", "en", "de", "es")) {
            var translations = YamlConfiguration.loadConfiguration(Path.of(
                    "../tropicube-core/src/main/resources/languages", language + ".yml").toFile());
            for (KitDefinition definition : definitions) {
                for (KitDefinition.KitItem item : definition.items()) {
                    assertFalse(translations.getString(KitContentPresentation.itemKey(item), "").isBlank(),
                            () -> language + ": " + KitContentPresentation.itemKey(item));
                    for (String enchantment : item.enchantments().keySet()) {
                        assertFalse(translations.getString(KitContentPresentation.enchantmentKey(enchantment), "").isBlank(),
                                () -> language + ": " + KitContentPresentation.enchantmentKey(enchantment));
                    }
                }
            }
        }
    }

    @Test
    void presentationKeysAndEnchantmentLevelsAreStable() {
        var potion = new KitDefinition.KitItem(Material.SPLASH_POTION, 1, 0,
                "LONG_SWIFTNESS", 0, Map.of());
        assertEquals("fk.kit-item-splash-potion-long-swiftness", KitContentPresentation.itemKey(potion));
        assertEquals("fk.kit-enchantment-fire-aspect", KitContentPresentation.enchantmentKey("fire_aspect"));
        assertEquals("I", KitContentPresentation.romanLevel(1));
        assertEquals("IV", KitContentPresentation.romanLevel(4));
        assertEquals("12", KitContentPresentation.romanLevel(12));
    }
}
