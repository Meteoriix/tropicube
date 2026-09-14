package fr.tropicube.fallenkingdoms.map;

import fr.tropicube.fallenkingdoms.game.KitCatalog;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BundledMapCatalogTest {
    @Test void bundledConfigurationContainsAPlayableGenericMapAndFourKits() throws Exception {
        try (var input = getClass().getResourceAsStream("/config.yml")) {
            var config = YamlConfiguration.loadConfiguration(new InputStreamReader(input, StandardCharsets.UTF_8));
            MapCatalog catalog = MapCatalog.load(config);
            assertEquals("cactus", catalog.select("cactus").id());
            assertEquals(5, catalog.select("cactus").bases().size());
            assertEquals(4, KitCatalog.load(config).definitions().size());
            assertTrue(config.getBoolean("game.auto-start"));
        }
    }
}
