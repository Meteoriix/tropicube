package fr.tropicube.lobby.managers;

import fr.tropicube.docker.model.InstanceMode;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SmartServerSelectorTest {
    @Test void prefersCountdownThenFillsWhileKeepingPartyTogether() {
        var waiting = server("waiting", 6, 8, "GAME_WAITING");
        var countdown = server("countdown", 3, 8, "GAME_STARTING");
        assertEquals("countdown", SmartServerSelector.select(List.of(waiting, countdown), 2).orElseThrow().id());
        assertEquals("countdown", SmartServerSelector.select(List.of(waiting, countdown), 4).orElseThrow().id());
        assertTrue(SmartServerSelector.select(List.of(waiting), 3).isEmpty());
    }

    private static LobbyServerManager.ServerInfo server(String id, int players, int maximum, String status) {
        return new LobbyServerManager.ServerInfo(id, "SHEEPWARS", "host", 25565, players, maximum,
                status, "sheepwars", InstanceMode.QUICK_PLAY, false, List.of());
    }
}
