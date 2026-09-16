package fr.tropicube.fallenkingdoms.map;

import fr.tropicube.fallenkingdoms.game.KitCatalog;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BundledMapCatalogTest {
    @Test void bundledConfigurationContainsAPlayableGenericMapAndFiveKits() throws Exception {
        try (var input = getClass().getResourceAsStream("/config.yml")) {
            var config = YamlConfiguration.loadConfiguration(new InputStreamReader(input, StandardCharsets.UTF_8));
            MapCatalog catalog = MapCatalog.load(config);
            assertEquals("cactus", catalog.select("cactus").id());
            assertEquals(5, catalog.select("cactus").bases().size());
            assertEquals(5, catalog.select("cactus").layouts().get(2).size());
            assertEquals(-712.0, catalog.select("cactus").lobby().x());
            assertEquals(59.0, catalog.select("cactus").lobby().y());
            assertEquals(5, KitCatalog.load(config).definitions().size());
            assertEquals(6, KitCatalog.load(config).definitions().get("alchemist").items().size());
            assertEquals(9,KitCatalog.load(config).definitions().get("enchanter").items().size());
            assertTrue(config.getBoolean("game.auto-start"));
        }
    }

    @Test void dockerMapAndBalanceConfigurationMatchesTheEmbeddedResource() throws Exception {
        var resource = getClass().getResourceAsStream("/config.yml");
        assertNotNull(resource);
        var embedded = YamlConfiguration.loadConfiguration(new InputStreamReader(resource, StandardCharsets.UTF_8));
        Path directory = Path.of("").toAbsolutePath();
        Path relative = Path.of("dockerfiles", "configs", "TropicubeFallenKingdoms", "config.yml");
        while (directory != null && !Files.isRegularFile(directory.resolve(relative))) directory = directory.getParent();
        assertNotNull(directory, "Racine du dépôt introuvable");
        var deployed = YamlConfiguration.loadConfiguration(directory.resolve(relative).toFile());
        for (String path : java.util.List.of("world-cycle.day-duration-seconds",
                "world-cycle.night-duration-seconds", "spawns.natural-hostile-night-retention",
                "drops.flint-base-chance", "drops.creeper-gunpowder-multiplier",
                "protections.enemy-base-barrier.render-interval-ticks", "locations.lobby.x",
                "locations.lobby.y", "locations.lobby.z")) {
            assertEquals(embedded.get(path), deployed.get(path), path);
        }
        assertEquals(embedded.getStringList("locations.maps.cactus.layouts.2"),
                deployed.getStringList("locations.maps.cactus.layouts.2"));
    }
}
