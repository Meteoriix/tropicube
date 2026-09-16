package fr.tropicube.velocity;

import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

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
    void bundledQuickPlayTemplatesStartWithTheProxy() throws IOException {
        ConfigurationNode config = loadBundledConfig();

        for (String templateId : new String[]{"sheepwars", "fallenkingdoms"}) {
            ConfigurationNode template = config.node("templates", templateId);
            assertTrue(template.node("auto-start").getBoolean());
            assertEquals(1, template.node("min-instances").getInt());
        }
    }

    @Test
    void bundledBetaQueuesUseMinimumFallenKingdomsTeamSizesAcrossSeveralKingdoms() throws IOException {
        ConfigurationNode config = loadBundledConfig();
        assertBetaQueue(config, "fallenkingdoms-beta-1v1", 2, 1, 25670, 25679);
        assertBetaQueue(config, "fallenkingdoms-beta-2v2", 4, 2, 25680, 25689);
    }

    @Test
    void developmentProfileLeavesCapacityForAnOnDemandFallenKingdomsQueue() throws IOException {
        ConfigurationNode config = loadRepositoryConfig("dockerfiles/configs/TropicubeVelocity/config.yml");
        ConfigurationNode regular = config.node("templates", "fallenkingdoms");

        assertFalse(regular.node("auto-start").getBoolean());
        assertEquals(0, regular.node("min-instances").getInt());

        long prewarmedMemory = reservedMemory(config.node("templates", "lobby"))
                + reservedMemory(config.node("templates", "sheepwars"));
        long betaMemory = reservedMemory(config.node("templates", "fallenkingdoms-beta-1v1"));
        assertTrue(prewarmedMemory + betaMemory <= config.node("docker", "memory-budget-mib").getLong());
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

    private ConfigurationNode loadRepositoryConfig(String relativePath) throws IOException {
        Path directory = Path.of("").toAbsolutePath();
        while (directory != null && !Files.isRegularFile(directory.resolve(relativePath))) {
            directory = directory.getParent();
        }
        assertNotNull(directory, "Racine du dépôt introuvable depuis le répertoire de test");
        return YamlConfigurationLoader.builder().path(directory.resolve(relativePath)).build().load();
    }

    private static long reservedMemory(ConfigurationNode template) {
        long maximumHeap = template.node("ram-max").getLong();
        long configuredOverhead = template.node("memory-overhead-mib").getLong();
        long overhead = configuredOverhead == 0 ? Math.max(512, (maximumHeap + 3) / 4) : configuredOverhead;
        return maximumHeap + overhead;
    }

    private static void assertBetaQueue(ConfigurationNode config, String templateId, int minimumPlayers,
                                        int minimumPlayersPerKingdom, int minimumPort, int maximumPort) {
        ConfigurationNode template = config.node("templates", templateId);
        assertTrue(template.node("enabled").getBoolean());
        assertEquals("BETA", template.node("type").getString());
        assertEquals(30, template.node("max-players").getInt());
        assertEquals(10, template.node("spectator-slots").getInt());
        assertEquals(minimumPort, template.node("port-min").getInt());
        assertEquals(maximumPort, template.node("port-max").getInt());
        assertFalse(template.node("auto-start").getBoolean());
        assertEquals(0, template.node("min-instances").getInt());
        ConfigurationNode environment = template.node("environment");
        assertEquals("QUICK_PLAY", environment.node("GAME_MODE").getString());
        assertEquals(Integer.toString(minimumPlayersPerKingdom),
                environment.node("FK_MIN_PLAYERS_PER_KINGDOM").getString());
        assertEquals("6",
                environment.node("FK_MAX_PLAYERS_PER_KINGDOM").getString());
        assertEquals("5", environment.node("FK_MAX_KINGDOMS").getString());
        assertEquals(minimumPlayers, minimumPlayersPerKingdom * 2);
    }
}
