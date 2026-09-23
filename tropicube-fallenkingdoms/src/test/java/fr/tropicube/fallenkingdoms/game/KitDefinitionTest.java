package fr.tropicube.fallenkingdoms.game;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
