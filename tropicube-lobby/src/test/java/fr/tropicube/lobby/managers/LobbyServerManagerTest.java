package fr.tropicube.lobby.managers;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    private static LobbyServerManager.ServerInfo server(String status) {
        return new LobbyServerManager.ServerInfo(
                "Sheepwars-test", "SHEEPWARS", "127.0.0.1", 25_625,
                0, 16, status, "sheepwars");
    }
}
