package fr.tropicube.tools.languages;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiFilesTest {
    @TempDir Path repository;

    @Test
    void discoversValidatesAndMirrorsAManifest() throws Exception {
        Path source = repository.resolve("tropicube-lobby/src/main/resources/scoreboards.yml");
        Files.createDirectories(source.getParent());
        Files.writeString(source, scoreboard("title.old"));
        UiFiles files = new UiFiles(repository);
        UiFiles.UiSnapshot snapshot = files.readAll().getFirst();

        assertTrue(files.validate("scoreboards", scoreboard("title.new")).isEmpty());
        Map<String, UiFiles.UiSnapshot> applied = files.apply(Map.of(snapshot.id(), snapshot.hash()),
                Map.of(snapshot.id(), scoreboard("title.new")));

        assertTrue(applied.get(snapshot.id()).content().contains("title.new"));
        assertEquals(Files.readString(source), Files.readString(
                repository.resolve("dockerfiles/configs/TropicubeLobby/scoreboards.yml")));
    }

    @Test
    void rejectsAmbiguousScoreboardLinesAndUnsafeMenuActions() {
        UiFiles files = new UiFiles(repository);
        assertFalse(files.validate("scoreboards", "version: 1\nscoreboards:\n  main:\n    title-key: title\n    variants:\n      idle:\n        lines:\n          - {blank: true, key: text}\n").isEmpty());
        assertFalse(files.validate("menus", "version: 1\nmenus:\n  main:\n    title-key: title\n    rows: 1\n    buttons:\n      bad: {slot: 0, material: STONE, action: 'say hello'}\n").isEmpty());
    }

    @Test
    void discoversAndValidatesTablists() throws Exception {
        Path source = repository.resolve("tropicube-lobby/src/main/resources/tablists.yml");
        Files.createDirectories(source.getParent());
        String valid = "version: 1\ntablists:\n  lobby:\n    variants:\n      default:\n"
                + "        header-key: lobby.tab-header\n        footer-key: lobby.tab-footer\n";
        Files.writeString(source, valid);

        UiFiles files = new UiFiles(repository);

        assertEquals("tablists", files.readAll().getFirst().type());
        assertTrue(files.validate("tablists", valid).isEmpty());
        assertFalse(files.validate("tablists", valid.replace("footer-key: lobby.tab-footer", "footer-key: ''"))
                .isEmpty());
    }

    private static String scoreboard(String title) {
        return "version: 1\nscoreboards:\n  lobby:\n    title-key: " + title
                + "\n    variants:\n      idle:\n        lines:\n          - {key: line}\n";
    }
}
