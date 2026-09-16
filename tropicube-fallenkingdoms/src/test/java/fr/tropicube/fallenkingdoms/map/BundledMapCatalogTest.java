package fr.tropicube.fallenkingdoms.map;

import fr.tropicube.fallenkingdoms.game.KitCatalog;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BundledMapCatalogTest {
    @Test void bundledConfigurationContainsAPlayableGenericMapAndFiveKits() throws Exception {
        try (var input = getClass().getResourceAsStream("/config.yml")) {
            var config = YamlConfiguration.loadConfiguration(new InputStreamReader(input, StandardCharsets.UTF_8));
            MapCatalog catalog = MapCatalog.load(config);
            assertEquals("cactus", catalog.select("cactus").id());
            assertEquals(5, catalog.select("cactus").bases().size());
            assertEquals(5, KitCatalog.load(config).definitions().size());
            assertEquals(6, KitCatalog.load(config).definitions().get("alchemist").items().size());
            assertEquals(9,KitCatalog.load(config).definitions().get("enchanter").items().size());
            assertTrue(config.getBoolean("game.auto-start"));
        }
    }
}
