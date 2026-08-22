package fr.tropicube.sheepwars.competitive;

import fr.tropicube.sheepwars.player.PlayerKit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KitMasteryCatalogTest {
    @Test void catalogDefinesTwoBranchesForEveryKit() throws Exception {
        try (var input = getClass().getResourceAsStream("/kit-mastery.yml")) {
            var yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(input, StandardCharsets.UTF_8));
            KitMasteryCatalog catalog = KitMasteryCatalog.parse(yaml);
            assertEquals(1, catalog.version());
            assertEquals(5, catalog.unlockLevel());
            for (PlayerKit kit : PlayerKit.values()) if (kit != PlayerKit.NONE) {
                for (KitMasteryBranch branch : KitMasteryBranch.values()) catalog.branch(kit, branch);
            }
        }
    }
}
