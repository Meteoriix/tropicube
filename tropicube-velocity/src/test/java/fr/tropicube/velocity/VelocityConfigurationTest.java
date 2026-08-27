package fr.tropicube.velocity;

import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VelocityConfigurationTest {

    @Test
    void bundledTemplatesAllowSeveralConcurrentInstances() throws IOException {
        ConfigurationNode config = loadBundledConfig();

        for (String templateId : new String[]{"lobby", "sheepwars"}) {
            ConfigurationNode template = config.node("templates", templateId);
            assertEquals(1, template.node("min-instances").getInt());
            assertEquals(5, template.node("max-instances").getInt());
            assertTrue(template.node("max-instances").getInt()
                    > template.node("min-instances").getInt());
        }
    }

    @Test
    void bundledPaperTemplatesPinThePrewarmedRuntime() throws IOException {
        ConfigurationNode config = loadBundledConfig();

        for (String templateId : new String[]{"lobby", "sheepwars"}) {
            ConfigurationNode environment = config.node("templates", templateId, "environment");
            assertEquals("PAPER", environment.node("TYPE").getString());
            assertEquals("26.2", environment.node("VERSION").getString());
            assertEquals("97", environment.node("PAPER_BUILD").getString());
        }
    }

    @Test
    void bundledConfigurationHasNoGradeOrOperatorAuthorizationFallback() throws IOException {
        ConfigurationNode config = loadBundledConfig();

        assertTrue(config.node("admin-uuids").virtual());
        assertTrue(config.node("nick", "allowed-grades").virtual());
        for (String template : new String[]{"lobby", "sheepwars"}) {
            assertTrue(config.node("templates", template, "environment", "OPS").virtual());
        }
    }

    @Test
    void bundledPartyConfigurationKeepsOneMinuteDisconnectGrace() throws IOException {
        ConfigurationNode config = loadBundledConfig();

        assertEquals(60, TropicubeVelocity.partyDisconnectGraceSeconds(config));
        config.node("party", "disconnect-grace-seconds").set(0);
        assertThrows(IllegalArgumentException.class,
                () -> TropicubeVelocity.partyDisconnectGraceSeconds(config));
    }

    @Test
    void bundledMaintenanceDeadlineIsValidated() throws IOException {
        ConfigurationNode config = loadBundledConfig();

        assertEquals(30, TropicubeVelocity.maintenanceDefaultDeadlineMinutes(config));
        config.node("maintenance", "default-deadline-minutes").set(0);
        assertThrows(IllegalArgumentException.class,
                () -> TropicubeVelocity.maintenanceDefaultDeadlineMinutes(config));
    }

    @Test
    void bundledMotdPromotesAvailableGamesWithoutLanguagesOrPlayerCount() throws IOException {
        ConfigurationNode motd = loadBundledConfig().node("motd");

        String line1 = motd.node("line-1").getString("");
        String line2 = motd.node("line-2").getString("");
        assertTrue(line1.contains("Des cubes, du soleil et de l’aventure !"));
        assertTrue(line2.contains("{games}"));
        assertFalse(line1.contains("FR / EN") || line2.contains("FR / EN"));
        assertFalse(line1.contains("{online}") || line2.contains("{online}"));
        assertEquals("SheepWars", motd.node("game-SHEEPWARS").getString());
    }

    private ConfigurationNode loadBundledConfig() throws IOException {
        var resource = getClass().getResource("/config.yml");
        assertNotNull(resource);
        return YamlConfigurationLoader.builder().url(resource).build().load();
    }
}
