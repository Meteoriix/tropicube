package fr.tropicube.sheepwars.powerup;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TeamPowerUpSettingsTest {
    @Test
    void loadsTheBundledDefaults() {
        TeamPowerUpSettings settings = TeamPowerUpSettings.load(loadDefaultConfiguration());

        assertTrue(settings.enabled());
        assertEquals(900, settings.respawnTicks());
        assertEquals(0.85, settings.hitRadius());
        assertEquals(100, settings.weights().values().stream().mapToInt(Integer::intValue).sum());
        assertEquals(3, settings.poisonArrowCount());
    }

    @Test
    void rejectsAnEmptyWeightTable() {
        YamlConfiguration configuration = loadDefaultConfiguration();
        for (TeamPowerUpType type : TeamPowerUpType.values()) {
            configuration.set("team-powerups.effects." + type.configKey() + ".weight", 0);
        }

        assertThrows(IllegalArgumentException.class, () -> TeamPowerUpSettings.load(configuration));
    }

    @Test
    void rejectsValuesThatWouldCreateUnsafeEffects() {
        YamlConfiguration configuration = loadDefaultConfiguration();
        configuration.set("team-powerups.effects.poison-arrows.arrow-count", 65);

        assertThrows(IllegalArgumentException.class, () -> TeamPowerUpSettings.load(configuration));
    }

    @Test
    void deployedTargetsAreConfiguredBetweenEveryPairOfBases() {
        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(
                Path.of("../dockerfiles/configs/TropicubeSheepwars/config.yml").toFile());
        ConfigurationSection maps = configuration.getConfigurationSection("locations");
        assertNotNull(maps);
        int checkedMaps = 0;
        for (String mapKey : maps.getKeys(false)) {
            if (!mapKey.startsWith("map_") || !maps.getBoolean(mapKey + ".enabled")) continue;
            checkedMaps++;
            ConfigurationSection targets = maps.getConfigurationSection(mapKey + ".powerups");
            assertNotNull(targets, mapKey);
            assertTrue(targets.getKeys(false).size() >= 5, mapKey + " doit proposer au moins cinq candidats");
            double[] red = spawnCenter(maps, mapKey + ".spawns.red");
            double[] blue = spawnCenter(maps, mapKey + ".spawns.blue");
            double dx = blue[0] - red[0];
            double dy = blue[1] - red[1];
            double dz = blue[2] - red[2];
            double lengthSquared = dx * dx + dy * dy + dz * dz;
            for (String targetKey : targets.getKeys(false)) {
                ConfigurationSection target = targets.getConfigurationSection(targetKey);
                assertNotNull(target, mapKey + "." + targetKey);
                double projection = ((target.getDouble("x") - red[0]) * dx
                        + (target.getDouble("y") - red[1]) * dy
                        + (target.getDouble("z") - red[2]) * dz) / lengthSquared;
                assertTrue(projection >= 0.25 && projection <= 0.75,
                        mapKey + "." + targetKey + " doit rester entre les deux bases");
            }
        }
        assertTrue(checkedMaps > 0, "Au moins une carte déployée doit être vérifiée");
    }

    private static double[] spawnCenter(ConfigurationSection maps, String path) {
        ConfigurationSection spawns = maps.getConfigurationSection(path);
        assertNotNull(spawns, path);
        double[] center = new double[3];
        for (String key : spawns.getKeys(false)) {
            center[0] += spawns.getDouble(key + ".x");
            center[1] += spawns.getDouble(key + ".y");
            center[2] += spawns.getDouble(key + ".z");
        }
        int count = spawns.getKeys(false).size();
        assertTrue(count > 0, path);
        center[0] /= count;
        center[1] /= count;
        center[2] /= count;
        return center;
    }

    private static YamlConfiguration loadDefaultConfiguration() {
        var stream = TeamPowerUpSettingsTest.class.getClassLoader().getResourceAsStream("config.yml");
        assertNotNull(stream);
        return YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
    }
}
