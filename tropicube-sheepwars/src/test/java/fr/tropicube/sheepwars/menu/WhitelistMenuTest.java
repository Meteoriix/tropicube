package fr.tropicube.sheepwars.menu;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WhitelistMenuTest {

    @Test
    void acceptsJavaFloodgateNamesAndCanonicalUuidsOnly() {
        assertTrue(WhitelistMenu.isValidIdentifier("Tropico_7"));
        assertTrue(WhitelistMenu.isValidIdentifier(".Bedrock_User"));
        assertTrue(WhitelistMenu.isValidIdentifier(" bcc80c2b-98ca-4b6c-b21b-33cf85e41228 "));
        assertFalse(WhitelistMenu.isValidIdentifier(""));
        assertFalse(WhitelistMenu.isValidIdentifier("Nom beaucoup trop long"));
        assertFalse(WhitelistMenu.isValidIdentifier("joueur:injecte"));
        assertFalse(WhitelistMenu.isValidIdentifier(".BedrockNameLongX"));
    }
}
