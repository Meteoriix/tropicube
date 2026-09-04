package fr.tropicube.core.menu;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkMenuStyleTest {

    @Test
    void recognizesGeyserClientBrand() {
        assertTrue(NetworkMenuStyle.isBedrockClient("BedrockPlayer", "Geyser"));
        assertTrue(NetworkMenuStyle.isBedrockClient("BedrockPlayer", "Geyser-Floodgate"));
    }

    @Test
    void usesFloodgatePrefixWhenClientBrandIsUnavailable() {
        assertTrue(NetworkMenuStyle.isBedrockClient(".BedrockPlayer", null));
    }

    @Test
    void preservesJavaMenuFrame() {
        assertFalse(NetworkMenuStyle.isBedrockClient("JavaPlayer", "vanilla"));
        assertFalse(NetworkMenuStyle.isBedrockClient("JavaPlayer", null));
    }
}
