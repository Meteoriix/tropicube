package fr.tropicube.fallenkingdoms.config;

import fr.tropicube.fallenkingdoms.game.CombatProfile;
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
            assertEquals(3, settings.minPlayersPerKingdom());
            assertEquals(30, settings.maximumPlayerCapacity());
            assertEquals(CombatProfile.LEGACY_1_8, settings.combatProfile());
            assertEquals(600, settings.timeline().pvpAt());
            assertEquals(1200, settings.timeline().assaultAt());
            assertEquals(2400, settings.timeline().suddenDeathAt());
            assertEquals(3600, settings.timeline().forceEndAt());
            assertEquals(300, settings.worldCycle().dayDurationSeconds());
            assertEquals(300, settings.worldCycle().nightDurationSeconds());
            assertEquals(0.50, settings.spawns().naturalHostileNightRetention());
            assertEquals(0.25, settings.drops().flintBaseChance());
            assertEquals(2.0, settings.drops().creeperGunpowderMultiplier());
            assertEquals(32, settings.enemyBaseBarrier().viewDistanceBlocks());
            assertEquals(40, settings.heartAlert().soundCooldownTicks());
            assertEquals(120, settings.heartAlert().durationTicks());
            assertEquals(10, settings.heartAlert().flashIntervalTicks());
            assertNotNull(config.getConfigurationSection("kits.definitions.alchemist"));
            config.set("protections.block-portal-bypass",false);
            assertThrows(IllegalArgumentException.class,()->FallenKingdomsSettings.load(config));
        }
    }

    @Test void worldBalanceValuesAreValidated() throws Exception {
        try(var input=getClass().getResourceAsStream("/config.yml")){
            var config=YamlConfiguration.loadConfiguration(new InputStreamReader(input,StandardCharsets.UTF_8));
            config.set("spawns.natural-hostile-night-retention",1.1);
            assertThrows(IllegalArgumentException.class,()->FallenKingdomsSettings.load(config));
            config.set("spawns.natural-hostile-night-retention",0.5);
            config.set("protections.enemy-base-barrier.render-interval-ticks",0);
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

    @Test void customGamesKeepTheHistoricalMinimumTeamSize() {
        assertEquals(3, FallenKingdomsSettings.defaultMinimumPlayersPerKingdom(3, false));
        assertEquals(4, FallenKingdomsSettings.defaultMinimumPlayersPerKingdom(3, true));
    }
}
