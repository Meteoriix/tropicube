package fr.tropicube.fallenkingdoms.listener;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class WaitingRoomContractTest {
    @Test
    void hotbarMatchesTheSheepWarsWaitingRoomNavigation() {
        assertEquals(List.of(0, 1, 2, 4, 7, 8), List.of(
                LobbyMenuListener.TEAM_SLOT,
                LobbyMenuListener.KIT_SLOT,
                LobbyMenuListener.MAP_SLOT,
                LobbyMenuListener.HOST_SLOT,
                LobbyMenuListener.PROFILE_SLOT,
                LobbyMenuListener.LEAVE_SLOT));
    }

    @Test
    void selectorsUseTheSharedFrameAndNavigationPositions() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(
                new File("src/main/resources/menus.yml"));
        for (String id : List.of("waiting-team", "waiting-kit", "waiting-map")) {
            ConfigurationSection menu = yaml.getConfigurationSection("menus." + id);
            assertNotNull(menu);
            assertEquals(3, menu.getInt("rows"));
            assertEquals("network", menu.getString("frame"));
            assertEquals(18, menu.getInt("buttons.back.slot"));
            assertEquals(26, menu.getInt("buttons.close.slot"));
        }
    }
}
