package fr.tropicube.sheepwars.util;

import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapsUtilTest {
    @Test
    void loadsGalionsWaterPoisonWithoutVoidDeath() {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set("hazards.void-kill-enabled", false);
        configuration.set("hazards.water-poison.enabled", true);
        configuration.set("hazards.water-poison.duration-ticks", 20);
        configuration.set("hazards.water-poison.amplifier", 0);

        var hazards = MapsUtil.loadHazards(configuration);

        assertFalse(hazards.voidKillEnabled());
        assertTrue(hazards.waterPoisonEnabled());
        assertEquals(20, hazards.waterPoisonDurationTicks());
        assertEquals(0, hazards.waterPoisonAmplifier());
    }

    @Test
    void keepsLegacyMapHazardsSafeAndRejectsInvalidPoison() {
        YamlConfiguration configuration = new YamlConfiguration();
        var hazards = MapsUtil.loadHazards(configuration);
        assertTrue(hazards.voidKillEnabled());
        assertFalse(hazards.waterPoisonEnabled());

        configuration.set("hazards.water-poison.duration-ticks", 0);
        assertThrows(IllegalArgumentException.class, () -> MapsUtil.loadHazards(configuration));
    }

    @Test
    void deployedGalionsUsesWaterPoisonAndKeepsEveryEnabledMapConfigured() {
        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(
                Path.of("../dockerfiles/configs/TropicubeSheepwars/config.yml").toFile());
        var locations = configuration.getConfigurationSection("locations");
        var galions = configuration.getConfigurationSection("locations.map_galions");

        var hazards = MapsUtil.loadHazards(galions);
        long enabledMaps = locations.getKeys(false).stream()
                .filter(key -> key.startsWith("map_") && locations.getBoolean(key + ".enabled"))
                .count();

        assertTrue(enabledMaps > 3, "Le catalogue déployé doit dépasser l'ancienne rotation de trois maps");
        assertFalse(hazards.voidKillEnabled());
        assertTrue(hazards.waterPoisonEnabled());
        assertEquals(20, hazards.waterPoisonDurationTicks());
        assertEquals(0, hazards.waterPoisonAmplifier());
    }

    @Test
    void loadsEveryNumberedPowerUpCandidateInNumericOrder() {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set("powerups.target12.x", 12.0);
        configuration.set("powerups.target1.x", 1.0);
        configuration.set("powerups.target9.x", 9.0);
        configuration.set("powerups.decorative.x", 99.0);
        World world = (World) Proxy.newProxyInstance(World.class.getClassLoader(),
                new Class<?>[] {World.class}, (_, _, _) -> null);

        var locations = MapsUtil.loadPowerUpSpawns(configuration, world);

        assertEquals(3, locations.size());
        assertEquals(1.0, locations.get(0).getX());
        assertEquals(9.0, locations.get(1).getX());
        assertEquals(12.0, locations.get(2).getX());
    }
}
