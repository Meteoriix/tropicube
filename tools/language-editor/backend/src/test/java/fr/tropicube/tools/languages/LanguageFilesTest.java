package fr.tropicube.tools.languages;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LanguageFilesTest {
    @Test
    void validatesMatchingKeysListsAndPlaceholders() {
        String french = "menu:\n  title: \"<gold>Bonjour {0}\"\n  lore:\n    - \"<gray>Ligne {1}\"\n";
        Map<String, String> documents = new LinkedHashMap<>();
        for (String language : LanguageFiles.LANGUAGES) documents.put(language, french);
        assertTrue(new LanguageFiles(java.nio.file.Path.of(".")).validate(documents).isEmpty());
    }

    @Test
    void reportsPlaceholderAndKeyDrift() {
        Map<String, String> documents = new LinkedHashMap<>();
        documents.put("fr", "message: \"Bonjour {0}\"\n");
        documents.put("en", "message: \"Hello\"\n");
        documents.put("de", "other: \"Hallo {0}\"\n");
        documents.put("es", "message: \"Hola {0}\"\n");
        var diagnostics = new LanguageFiles(java.nio.file.Path.of(".")).validate(documents);
        assertEquals(2, diagnostics.size());
    }

    @Test
    void reportsUnknownMiniMessageTag() {
        Map<String, String> documents = new LinkedHashMap<>();
        for (String language : LanguageFiles.LANGUAGES) documents.put(language, "message: \"<blink>Texte\"\n");
        assertEquals(4, new LanguageFiles(java.nio.file.Path.of(".")).validate(documents).size());
    }

    @Test
    void acceptsEveryStandardMiniMessageTagIncludingLineBreaks() {
        String text = "message: \"<gray>Ligne 1<br><newline><gradient:red:blue>Line 2</gradient>\"\n";
        Map<String, String> documents = new LinkedHashMap<>();
        for (String language : LanguageFiles.LANGUAGES) documents.put(language, text);
        assertTrue(new LanguageFiles(java.nio.file.Path.of(".")).validate(documents).isEmpty());
    }

    @Test
    void rejectsDuplicateKeys() {
        Map<String, String> documents = new LinkedHashMap<>();
        for (String language : LanguageFiles.LANGUAGES) documents.put(language, "message: one\nmessage: two\n");
        assertEquals(4, new LanguageFiles(java.nio.file.Path.of(".")).validate(documents).size());
    }

    @Test
    void reportsPaletteDrift() {
        Map<String, String> documents = new LinkedHashMap<>();
        documents.put("fr", "message: \"<gold>Bonjour\"\n");
        documents.put("en", "message: \"<green>Hello\"\n");
        documents.put("de", "message: \"<gold>Hallo\"\n");
        documents.put("es", "message: \"<gold>Hola\"\n");
        assertEquals("palette-parity", new LanguageFiles(java.nio.file.Path.of(".")).validate(documents).getFirst().code());
    }
}
