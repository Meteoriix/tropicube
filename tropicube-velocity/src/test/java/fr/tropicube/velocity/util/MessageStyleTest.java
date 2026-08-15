package fr.tropicube.velocity.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class MessageStyleTest {

    @Test
    void rendersTheNetworkBrandWithoutDecorativeBrackets() {
        assertEquals("TROPICUBE > Serveur disponible", MessageStyle.plain("<tc><green>Serveur disponible"));
    }

    @Test
    void technicalLogDoesNotLeakMiniMessageTags() {
        String rendered = MessageStyle.log("docker", "<yellow>Instance en attente");
        assertFalse(rendered.contains("<yellow>"));
        assertFalse(rendered.contains("[Tropicube]"));
    }
}
