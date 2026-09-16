package fr.tropicube.fallenkingdoms.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class FallenKingdomsSettingsTest {
    @Test void bundledSettingsAreValidAndMandatoryProtectionsCannotBeDisabled() throws Exception {
        try(var input=getClass().getResourceAsStream("/config.yml")){
            var config=YamlConfiguration.loadConfiguration(new InputStreamReader(input,StandardCharsets.UTF_8));
            var settings=FallenKingdomsSettings.load(config);
            assertEquals(500.0,settings.heartHealth());assertFalse(config.getBoolean("combat.friendly-fire"));
            assertEquals(300, settings.timeline().pvpAt());
            assertEquals(1800, settings.timeline().suddenDeathAt());
            assertEquals(2700, settings.timeline().forceEndAt());
            assertNotNull(config.getConfigurationSection("kits.definitions.alchemist"));
            config.set("protections.block-portal-bypass",false);
            assertThrows(IllegalArgumentException.class,()->FallenKingdomsSettings.load(config));
        }
    }

    @Test void phaseOrderIsValidated() throws Exception {
        try(var input=getClass().getResourceAsStream("/config.yml")){
            var config=YamlConfiguration.loadConfiguration(new InputStreamReader(input,StandardCharsets.UTF_8));
            config.set("phases.assault-at-seconds",300);
            assertThrows(IllegalArgumentException.class,()->FallenKingdomsSettings.load(config));
        }
    }
}
