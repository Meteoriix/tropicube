package fr.tropicube.lobby.gui;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LanguageSelectorGUITest {
    @Test
    void readsUnifiedLoreAndMigratesLegacyLinesInMemory() {
        assertEquals("Première<br>Deuxième",
                LanguageSelectorGUI.languageLore(Map.of("lore", "Première<br>Deuxième")));
        assertEquals("Première<br>Deuxième",
                LanguageSelectorGUI.languageLore(Map.of("lore1", "Première", "lore2", "Deuxième")));
        assertEquals("Deuxième", LanguageSelectorGUI.languageLore(Map.of("lore2", "Deuxième")));
    }
}
