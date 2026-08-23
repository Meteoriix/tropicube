package fr.tropicube.lobby.managers;

import fr.tropicube.docker.model.InstanceMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

class LobbyServerManagerTest {

    @Test
    void listsOnlyStartingOrActiveGameStates() {
        assertTrue(server("STARTING").isListed());
        assertTrue(server("GAME_WAITING").isListed());
        assertTrue(server("GAME_STARTING").isListed());
        assertTrue(server("GAME_PLAYING").isListed());
        assertTrue(server("GAME_ENDING").isListed());

        assertFalse(server("STOPPING").isListed());
        assertFalse(server("STOPPED").isListed());
        assertFalse(server("ERROR").isListed());
    }

    @Test
    void recognizesThePublishedPlayingStatusAndKeepsItJoinableForSpectators() {
        assertTrue(server("GAME_PLAYING").isPlaying());
        assertTrue(server("PLAYING").isPlaying());
        assertTrue(server("GAME_PLAYING").isJoinable());
        assertFalse(server("GAME_WAITING").isPlaying());
    }

    @Test
    void hidesPrivateServersFromPlayersOutsideTheWhitelist() {
        UUID member = UUID.randomUUID();
        LobbyServerManager.ServerInfo privateServer = new LobbyServerManager.ServerInfo(
                "Sheepwars-private", "SHEEPWARS", "127.0.0.1", 25_625,
                0, 16, "GAME_WAITING", "sheepwars", InstanceMode.CUSTOM, true, List.of(member));

        assertTrue(privateServer.isVisibleTo(member));
        assertFalse(privateServer.isVisibleTo(UUID.randomUUID()));
        assertFalse(privateServer.isVisibleTo(null));
    }

    private static LobbyServerManager.ServerInfo server(String status) {
        return new LobbyServerManager.ServerInfo(
                "Sheepwars-test", "SHEEPWARS", "127.0.0.1", 25_625,
                0, 16, status, "sheepwars", InstanceMode.QUICK_PLAY, false, List.of());
    }
}
