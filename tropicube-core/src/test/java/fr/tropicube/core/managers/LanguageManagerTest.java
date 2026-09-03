package fr.tropicube.core.managers;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LanguageManagerTest {
    @Test
    void resolvesSupportedMinecraftLocales() {
        assertEquals("fr", LanguageManager.resolveClientLanguage("fr_fr"));
        assertEquals("en", LanguageManager.resolveClientLanguage("en_US"));
        assertEquals("de", LanguageManager.resolveClientLanguage("de-DE"));
        assertEquals("es", LanguageManager.resolveClientLanguage("es_mx"));
    }

    @Test
    void fallsBackToEnglishForUnsupportedOrMissingLocales() {
        assertEquals("en", LanguageManager.resolveClientLanguage("pt_br"));
        assertEquals("en", LanguageManager.resolveClientLanguage(""));
        assertEquals("en", LanguageManager.resolveClientLanguage(null));
    }

    @Test
    void resolvesTheVisibleInstanceNameWithSafeFallbacks() {
        assertEquals("SheepWars-7f42a1b3",
                LanguageManager.resolveInstanceName("SheepWars-7f42a1b3", "uuid", "server"));
        assertEquals("uuid", LanguageManager.resolveInstanceName(" ", "uuid", "server"));
        assertEquals("server", LanguageManager.resolveInstanceName(null, "", "server"));
    }
}
