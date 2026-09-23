package fr.tropicube.fallenkingdoms.loot;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class LootTablesTest {
    @Test void bundledTablesCoverDaysTwoToSixWithThreeDeterministicDraws() {
        var input = getClass().getResourceAsStream("/config.yml");
        assertNotNull(input);
        var config = YamlConfiguration.loadConfiguration(new InputStreamReader(input, StandardCharsets.UTF_8));
        LootTables tables = LootTables.load(config);
        LootRoller roller = new LootRoller();
        for (int day = 2; day <= 6; day++) {
            DailyLootTable table = tables.forDay(day);
            assertEquals(3, table.rolls());
            assertEquals(100, table.totalWeight());
            List<LootRoller.LootStack> first = roller.roll(table, new Random(42));
            List<LootRoller.LootStack> second = roller.roll(table, new Random(42));
            assertEquals(first, second);
            assertTrue(first.stream().mapToInt(LootRoller.LootStack::amount).sum() >= 3);
        }
    }

    @Test void entriesRejectInvalidQuantitiesAndWeights() {
        assertThrows(IllegalArgumentException.class,
                () -> new WeightedLootEntry(Material.DIAMOND, 0, 1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new WeightedLootEntry(Material.DIAMOND, 1, 65, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new WeightedLootEntry(Material.DIAMOND, 1, 1, 0));
    }

    @Test void loadingRejectsAMissingDailyTable() {
        var config = new YamlConfiguration();
        config.set("progressive-loot.rolls-per-chest", 3);
        for (int day = 2; day <= 5; day++) config.set("progressive-loot.tables." + day,
                List.of(Map.of("material", "DIAMOND", "min", 1, "max", 1, "weight", 1)));

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> LootTables.load(config));

        assertTrue(failure.getMessage().contains("progressive-loot.tables.6"));
    }
}
