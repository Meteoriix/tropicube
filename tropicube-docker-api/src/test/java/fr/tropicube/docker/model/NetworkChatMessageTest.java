package fr.tropicube.docker.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NetworkChatMessageTest {
    @Test void roundTripsAndValidatesLength() {
        NetworkChatMessage message = new NetworkChatMessage("abc", UUID.randomUUID(), "Player",
                "Bonjour", "lobby-1", "GLOBAL", 1);
        assertEquals(message, NetworkChatMessage.fromJson(message.toJson()));
        assertThrows(IllegalArgumentException.class, () -> new NetworkChatMessage("abc", UUID.randomUUID(),
                "Player", "x".repeat(513), "lobby-1", "GLOBAL", 1));
    }
}
