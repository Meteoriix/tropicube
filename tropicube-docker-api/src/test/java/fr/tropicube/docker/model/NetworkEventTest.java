package fr.tropicube.docker.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NetworkEventTest {
    @Test
    void roundTrips() {
        NetworkEvent event = NetworkEvent.create("CHAT_GLOBAL", "lobby-1", "hello");
        assertEquals(event, NetworkEvent.fromJson(event.toJson()));
    }

    @Test
    void validatesBoundary() {
        assertThrows(IllegalArgumentException.class, () -> NetworkEvent.fromJson("{}"));
    }
}
