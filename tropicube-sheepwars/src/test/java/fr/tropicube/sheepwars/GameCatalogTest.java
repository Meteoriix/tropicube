package fr.tropicube.sheepwars;

import fr.tropicube.sheepwars.player.PlayerClass;
import fr.tropicube.sheepwars.config.GameplayBalance;
import fr.tropicube.sheepwars.player.PlayerKit;
import fr.tropicube.sheepwars.sheep.SheepType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameCatalogTest {

    @Test
    void everyPlayableClassHasExactlyThreeCompatibleKits() {
        for (PlayerClass playerClass : PlayerClass.values()) {
            PlayerKit[] kits = PlayerKit.getKitsForClass(playerClass);
            int expectedCount = playerClass == PlayerClass.NONE ? 1 : 3;

            assertEquals(expectedCount, kits.length, playerClass.name());
            assertTrue(Arrays.stream(kits).allMatch(kit -> kit.getPlayerClass() == playerClass));
            assertTrue(Arrays.stream(kits).allMatch(kit -> !kit.getDisplayName().isBlank()));
            assertTrue(Arrays.stream(kits).allMatch(kit -> !kit.getDescription().isBlank()));
        }
    }

    @Test
    void defaultSheepWeightsMatchTheCatalogAndFormACompleteDistribution() {
        YamlConfiguration configuration = loadDefaultConfiguration();
        ConfigurationSection weights = configuration.getConfigurationSection(
                "default-settings.sheep-probabilities");
        assertNotNull(weights);

        Set<String> catalogKeys = Arrays.stream(SheepType.values())
                .map(SheepType::getConfigKey)
                .collect(Collectors.toSet());
        assertEquals(SheepType.values().length, catalogKeys.size(), "Les clés du catalogue doivent être uniques");
        assertEquals(catalogKeys, weights.getKeys(false), "Le catalogue et config.yml doivent rester synchronisés");

        int totalWeight = catalogKeys.stream().mapToInt(weights::getInt).sum();
        assertEquals(100, totalWeight, "Les probabilités par défaut doivent totaliser 100");
        assertTrue(catalogKeys.stream().allMatch(key -> weights.getInt(key) > 0));

        Map<String, Integer> expectedWeights = Map.ofEntries(
                Map.entry("boarding", 8), Map.entry("tnt", 10), Map.entry("distort", 3),
                Map.entry("darkness", 6), Map.entry("searching", 7), Map.entry("fire", 8),
                Map.entry("poison", 6), Map.entry("swap", 7), Map.entry("meteor", 4),
                Map.entry("healing", 10), Map.entry("lightning", 8), Map.entry("gravity", 5),
                Map.entry("mecha", 3), Map.entry("strength", 9), Map.entry("fragmentation", 6));
        expectedWeights.forEach((key, expected) -> assertEquals(expected, weights.getInt(key), key));
    }

    @Test
    void catalogPresentationFieldsAreComplete() {
        for (SheepType type : SheepType.values()) {
            assertFalse(type.getDisplayName().isBlank(), type.name());
            assertFalse(type.getDescription().isBlank(), type.name());
            assertFalse(type.getConfigKey().isBlank(), type.name());
            assertNotNull(type.getWool(), type.name());
            assertNotNull(type.getTextColor(), type.name());
        }
    }

    @Test
    void autoStartDefaultsDifferBetweenClassicAndCustomGames() {
        YamlConfiguration configuration = loadDefaultConfiguration();

        assertTrue(configuration.getBoolean("default-settings.auto-start"));
        assertFalse(configuration.getBoolean("custom-game-default-settings.auto-start"));
    }

    @Test
    void defaultGameplayBalanceIsCompleteAndValid() {
        GameplayBalance balance = GameplayBalance.load(loadDefaultConfiguration());

        assertEquals(1.2, balance.decimal("kits.dps-sheep-damage-multiplier"), 0.0001);
        assertEquals(30, balance.ticks("global.countdown-seconds"));
        assertEquals(8, balance.integer("sheep.fragmentation.total-damage-cap"));
    }

    @Test
    void defaultSheepDistributionProvidesThirtyTotalDrawsPerFullGame() {
        YamlConfiguration configuration = loadDefaultConfiguration();

        int duration = configuration.getInt("default-settings.game-duration");
        int delay = configuration.getInt("default-settings.sheep-give-delay");
        assertEquals(20, delay);
        int usefulPeriodicDeliveries = (duration - 1) / delay;
        assertEquals(29, usefulPeriodicDeliveries);
        assertEquals(30, 1 + usefulPeriodicDeliveries);
    }

    private static YamlConfiguration loadDefaultConfiguration() {
        InputStream stream = GameCatalogTest.class.getClassLoader().getResourceAsStream("config.yml");
        assertNotNull(stream, "config.yml doit être présent dans les ressources du module");
        return YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
    }
}
