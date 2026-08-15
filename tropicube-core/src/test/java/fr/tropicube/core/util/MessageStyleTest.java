package fr.tropicube.core.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class MessageStyleTest {

    @Test
    void rendersNetworkAndGameBrandsWithoutDecorativeBrackets() {
        assertEquals("TROPICUBE > Opération réussie", MessageStyle.plain("<tc><green>Opération réussie"));
        assertEquals("SHEEPWARS > Partie lancée", MessageStyle.plain("<sw><green>Partie lancée"));
    }

    @Test
    void technicalLogHasAReadablePlainFallback() {
        String rendered = MessageStyle.log("tc", "redis", "<green>Connexion établie");
        assertFalse(rendered.contains("<green>"));
        assertFalse(rendered.contains("[Tropicube]"));
    }
}
