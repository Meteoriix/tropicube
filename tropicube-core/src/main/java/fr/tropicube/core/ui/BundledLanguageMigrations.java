package fr.tropicube.core.ui;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Migrates unchanged former bundled translations while preserving editor customizations. */
final class BundledLanguageMigrations {
    private static final Map<String, List<ValueMigration>> MIGRATIONS = Map.of(
            "core/languages/fr.yml", List.of(
                    new ValueMigration("lobby.hotbar-servers-name", "<aqua>⚡ Sélecteur de serveur", "<green>⚡ Jeux"),
                    new ValueMigration("lobby.hotbar-servers-name", "<green>⚡ Jeux", "<dark_aqua>⚡ Jeux"),
                    new ValueMigration("lobby.type-lore-left-click", "<yellow>Clic gauche : choisir le type de partie",
                            "<green>▶ Clic gauche : file Quick Play."),
                    new ValueMigration("social.hotbar-name", "<aqua>☀ Social", "<green>☀ Social")),
            "core/languages/en.yml", List.of(
                    new ValueMigration("lobby.hotbar-servers-name", "<aqua>⚡ Server Selector", "<green>⚡ Games"),
                    new ValueMigration("lobby.hotbar-servers-name", "<green>⚡ Games", "<dark_aqua>⚡ Games"),
                    new ValueMigration("lobby.type-lore-left-click", "<yellow>Left click: choose a match type",
                            "<green>▶ Left click: Quick Play queue."),
                    new ValueMigration("social.hotbar-name", "<aqua>☀ Social", "<green>☀ Social")),
            "core/languages/de.yml", List.of(
                    new ValueMigration("lobby.hotbar-servers-name", "<aqua>⚡ Serverauswahl", "<green>⚡ Spiele"),
                    new ValueMigration("lobby.hotbar-servers-name", "<green>⚡ Spiele", "<dark_aqua>⚡ Spiele"),
                    new ValueMigration("lobby.type-lore-left-click", "<yellow>Linksklick: Art der Partie wählen",
                            "<green>▶ Linksklick: Quick-Play-Warteschlange."),
                    new ValueMigration("social.hotbar-name", "<aqua>☀ Sozial", "<green>☀ Sozial")),
            "core/languages/es.yml", List.of(
                    new ValueMigration("lobby.hotbar-servers-name", "<aqua>⚡ Selector de servidor", "<green>⚡ Juegos"),
                    new ValueMigration("lobby.hotbar-servers-name", "<green>⚡ Juegos", "<dark_aqua>⚡ Juegos"),
                    new ValueMigration("lobby.type-lore-left-click", "<yellow>Clic izquierdo: elegir el tipo de partida",
                            "<green>▶ Clic izquierdo: cola Quick Play."),
                    new ValueMigration("social.hotbar-name", "<aqua>☀ Social", "<green>☀ Social")));

    private BundledLanguageMigrations() {}

    static void apply(String id, Path file) throws IOException {
        List<ValueMigration> migrations = MIGRATIONS.get(id);
        if (migrations == null) return;

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file.toFile());
        String normalized = Files.readString(file, StandardCharsets.UTF_8)
                .replace("\r\n", "\n").replace('\r', '\n');
        List<String> lines = new ArrayList<>(List.of(normalized.split("\n", -1)));
        boolean changed = false;
        for (ValueMigration migration : migrations) {
            if (!Objects.equals(migration.previousValue(), yaml.getString(migration.path()))) continue;
            int line = findScalarLine(lines, migration.path());
            if (line < 0) continue;
            lines.set(line, replaceScalar(lines.get(line), migration.currentValue()));
            yaml.set(migration.path(), migration.currentValue());
            changed = true;
        }
        if (changed) Files.writeString(file, String.join("\n", lines), StandardCharsets.UTF_8);
    }

    private static int findScalarLine(List<String> lines, String path) {
        String[] keys = path.split("\\.");
        String section = null;
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            if (line.isBlank() || line.stripLeading().startsWith("#")) continue;
            int indentation = line.length() - line.stripLeading().length();
            String trimmed = line.substring(indentation);
            int colon = trimmed.indexOf(':');
            if (colon <= 0) continue;
            String key = trimmed.substring(0, colon).trim();
            if (indentation == 0) section = key;
            else if (indentation == 2 && keys.length == 2 && keys[0].equals(section) && keys[1].equals(key)) return index;
        }
        return -1;
    }

    private static String replaceScalar(String line, String value) {
        int colon = line.indexOf(':');
        int comment = findComment(line, colon + 1);
        String suffix = comment < 0 ? "" : " " + line.substring(comment).stripLeading();
        return line.substring(0, colon + 1) + " \"" + value.replace("\\", "\\\\").replace("\"", "\\\"")
                + "\"" + suffix;
    }

    private static int findComment(String line, int start) {
        boolean quoted = false;
        boolean escaped = false;
        for (int index = start; index < line.length(); index++) {
            char character = line.charAt(index);
            if (escaped) escaped = false;
            else if (character == '\\' && quoted) escaped = true;
            else if (character == '"') quoted = !quoted;
            else if (character == '#' && !quoted) return index;
        }
        return -1;
    }

    private record ValueMigration(String path, String previousValue, String currentValue) {}
}
