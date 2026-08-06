package fr.tropicube.core.util;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/**
 * Fusionne les nouvelles clés d'une ressource embarquée avec le fichier sur disque,
 * sans modifier les valeurs existantes ni supprimer les commentaires utilisateur.
 * <p>
 * {@code YamlConfiguration} détecte les chemins absents, puis une insertion
 * textuelle les replace dans leur section tout en conservant la mise en forme.
 */
public final class ConfigUpdater {

    private ConfigUpdater() {}

    /**
     * Ajoute les clés de la ressource absentes de {@code diskFile}.
     * Le contenu existant n'est jamais modifié.
     *
     * @param plugin plugin propriétaire de la ressource
     * @param resourcePath chemin interne au JAR, par exemple {@code languages/fr.yml}
     * @param diskFile fichier à mettre à jour sur disque
     */
    public static void update(Plugin plugin, String resourcePath, File diskFile) throws IOException {
        if (!diskFile.exists()) return;

        InputStream stream = plugin.getResource(resourcePath);
        if (stream == null) return;

        String defaultsText = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        YamlConfiguration defaults = loadYaml(defaultsText);
        YamlConfiguration disk    = YamlConfiguration.loadConfiguration(diskFile);

        // Recense les feuilles présentes par défaut mais absentes du disque.
        List<String> missing = new ArrayList<>();
        for (String key : defaults.getKeys(true)) {
            if (!defaults.isConfigurationSection(key) && !disk.isSet(key)) {
                missing.add(key);
            }
        }
        if (missing.isEmpty()) return;

        plugin.getLogger().info("[ConfigUpdater] " + diskFile.getName()
                + ": inserting " + missing.size() + " missing key(s).");

        // Travaille sur le texte brut pour préserver commentaires et ordre des clés.
        String diskRaw = Files.readString(diskFile.toPath(), StandardCharsets.UTF_8)
                              .replace("\r\n", "\n").replace('\r', '\n');
        List<String> lines   = new ArrayList<>(Arrays.asList(diskRaw.split("\n", -1)));
        List<String> defLines = Arrays.asList(defaultsText.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1));

        // Regroupe les clés par section ; une clé sans point est un scalaire racine.
        Map<String, List<String>> bySection = new LinkedHashMap<>();
        for (String key : missing) {
            int dot = key.indexOf('.');
            String section = dot == -1 ? "\0" + key : key.substring(0, dot);
            bySection.computeIfAbsent(section, k -> new ArrayList<>()).add(key);
        }

        // Prépare les insertions, ensuite appliquées du bas vers le haut.
        List<int[]> insertIndices = new ArrayList<>();
        List<List<String>> insertBlocks = new ArrayList<>();
        Set<String> insertedDeepPaths = new HashSet<>();

        for (Map.Entry<String, List<String>> entry : bySection.entrySet()) {
            String section   = entry.getKey();
            List<String> keys = entry.getValue();

            boolean topLevelScalar = section.startsWith("\0");
            String realSection     = topLevelScalar ? section.substring(1) : section;

            if (topLevelScalar || !disk.isConfigurationSection(realSection)) {
                // Ajoute en fin de fichier une section ou valeur racine entièrement absente.
                String block = topLevelScalar
                        ? extractTopLevelKeyBlock(defLines, realSection)
                        : extractSectionBlock(defLines, realSection);
                if (block.isEmpty()) continue;

                List<String> blockLines = new ArrayList<>();
                // Ajoute un séparateur seulement si le fichier n'en possède pas déjà un.
                if (!lines.isEmpty() && !lines.get(lines.size() - 1).isBlank()) {
                    blockLines.add("");
                }
                blockLines.addAll(Arrays.asList(block.split("\n", -1)));
                insertIndices.add(new int[]{lines.size(), insertBlocks.size()});
                insertBlocks.add(blockLines);
            } else {
                // Insère les sous-clés après le dernier contenu de leur section.
                int insertAt = findSectionInsertPoint(lines, realSection);
                List<String> blockLines = new ArrayList<>();
                for (String fullKey : keys) {
                    int dot = fullKey.indexOf('.');
                    String subKey = dot == -1 ? fullKey : fullKey.substring(dot + 1);
                    if (subKey.contains(".")) {
                        String blockPath = fullKey;
                        String parentPath = parentPath(blockPath);
                        while (!parentPath.isEmpty() && !disk.isConfigurationSection(parentPath)) {
                            blockPath = parentPath;
                            parentPath = parentPath(blockPath);
                        }
                        if (parentPath.isEmpty() || !insertedDeepPaths.add(blockPath)) continue;
                        String keyText = extractPathBlock(defLines, blockPath);
                        if (!keyText.isEmpty()) {
                            int deepInsertAt = findPathInsertPoint(lines, parentPath);
                            insertIndices.add(new int[]{deepInsertAt, insertBlocks.size()});
                            insertBlocks.add(new ArrayList<>(Arrays.asList(keyText.split("\n", -1))));
                        }
                        continue;
                    }
                    String keyText = extractSubKeyBlock(defLines, realSection, subKey);
                    if (!keyText.isEmpty()) {
                        blockLines.addAll(Arrays.asList(keyText.split("\n", -1)));
                    }
                }
                if (!blockLines.isEmpty()) {
                    insertIndices.add(new int[]{insertAt, insertBlocks.size()});
                    insertBlocks.add(blockLines);
                }
            }
        }

        // Applique les insertions en ordre inverse afin de préserver les index précédents.
        insertIndices.sort((a, b) -> b[0] - a[0]);
        for (int[] ip : insertIndices) {
            int at = Math.min(ip[0], lines.size());
            lines.addAll(at, insertBlocks.get(ip[1]));
        }

        Files.writeString(diskFile.toPath(), String.join("\n", lines), StandardCharsets.UTF_8);
        plugin.getLogger().info("[ConfigUpdater] " + diskFile.getName() + " updated successfully.");
    }

    // Analyse textuelle du YAML

    private static YamlConfiguration loadYaml(String text) {
        YamlConfiguration cfg = new YamlConfiguration();
        try { cfg.loadFromString(text); } catch (Exception ignored) {}
        return cfg;
    }

    /**
     * Renvoie l'index suivant la dernière ligne indentée d'une section racine.
     * Les lignes vides et commentaires séparant deux sections ne sont pas inclus.
     */
    private static int findSectionInsertPoint(List<String> lines, String section) {
        int start = findSectionStart(lines, section);
        if (start == -1) return lines.size();

        int lastContent = start;
        for (int i = start + 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (!line.isEmpty()) {
                char first = line.charAt(0);
                if (first != ' ' && first != '\t' && first != '#') break; // next top-level key
                if (first == ' ' || first == '\t') lastContent = i;       // indented = section content
            }
        }
        return lastContent + 1;
    }

    private static int findSectionStart(List<String> lines, String section) {
        for (int i = 0; i < lines.size(); i++) {
            if (isSectionHeader(lines.get(i), section)) return i;
        }
        return -1;
    }

    private static boolean isSectionHeader(String line, String section) {
        if (!line.startsWith(section + ":")) return false;
        if (line.length() == section.length() + 1) return true;
        char next = line.charAt(section.length() + 1);
        return next == ' ' || next == '\t';
    }

    /**
     * Extrait une section racine complète et ses commentaires contigus.
     */
    private static String extractSectionBlock(List<String> defLines, String section) {
        int sectionStart = -1;
        for (int i = 0; i < defLines.size(); i++) {
            if (isSectionHeader(defLines.get(i), section)) { sectionStart = i; break; }
        }
        if (sectionStart == -1) return "";

        // Remonte sur les commentaires contigus, sans franchir une ligne vide.
        int blockStart = sectionStart;
        for (int i = sectionStart - 1; i >= 0; i--) {
            if (defLines.get(i).startsWith("#")) blockStart = i;
            else break;
        }

        // La prochaine clé racine termine la section.
        int end = defLines.size();
        for (int i = sectionStart + 1; i < defLines.size(); i++) {
            String line = defLines.get(i);
            if (!line.isEmpty() && line.charAt(0) != ' ' && line.charAt(0) != '\t' && line.charAt(0) != '#') {
                end = i;
                break;
            }
        }
        return String.join("\n", defLines.subList(blockStart, end));
    }

    /**
     * Extrait une valeur scalaire isolée à la racine.
     */
    private static String extractTopLevelKeyBlock(List<String> defLines, String key) {
        String prefix = key + ":";
        for (int i = 0; i < defLines.size(); i++) {
            String line = defLines.get(i);
            if (line.equals(prefix) || line.startsWith(prefix + " ") || line.startsWith(prefix + "\t")) {
                // Inclut les commentaires contigus précédents.
                int blockStart = i;
                for (int j = i - 1; j >= 0; j--) {
                    if (defLines.get(j).startsWith("#")) blockStart = j;
                    else break;
                }
                return String.join("\n", defLines.subList(blockStart, i + 1));
            }
        }
        return "";
    }

    /**
     * Extrait une sous-clé et ses commentaires contigus dans une section donnée.
     *
     * @param section nom de la section racine
     * @param subKey nom direct de la sous-clé, sans point
     */
    private static String extractSubKeyBlock(List<String> defLines, String section, String subKey) {
        int sectionStart = -1;
        for (int i = 0; i < defLines.size(); i++) {
            if (isSectionHeader(defLines.get(i), section)) { sectionStart = i; break; }
        }
        if (sectionStart == -1) return "";

        int sectionEnd = defLines.size();
        for (int i = sectionStart + 1; i < defLines.size(); i++) {
            String line = defLines.get(i);
            if (!line.isEmpty() && line.charAt(0) != ' ' && line.charAt(0) != '\t' && line.charAt(0) != '#') {
                sectionEnd = i;
                break;
            }
        }

        // Localise la clé attendue avec deux espaces d'indentation.
        String keyPrefix = "  " + subKey + ":";
        int keyLine = -1;
        for (int i = sectionStart + 1; i < sectionEnd; i++) {
            String line = defLines.get(i);
            if (line.equals(keyPrefix) || line.startsWith(keyPrefix + " ") || line.startsWith(keyPrefix + "\t")) {
                keyLine = i;
                break;
            }
        }
        if (keyLine == -1) return "";

        // Inclut les commentaires contigus précédents dans la même section.
        int blockStart = keyLine;
        for (int i = keyLine - 1; i > sectionStart; i--) {
            String line = defLines.get(i);
            if (line.trim().startsWith("#")) blockStart = i;
            else break;
        }

        // Inclut les descendants indentés d'au moins quatre espaces.
        int blockEnd = keyLine + 1;
        for (int i = keyLine + 1; i < sectionEnd; i++) {
            String line = defLines.get(i);
            if (line.isBlank()) break;
            int indent = leadingSpaces(line);
            if (indent <= 2) break; // sibling or section header
            blockEnd = i + 1;
        }

        return String.join("\n", defLines.subList(blockStart, blockEnd));
    }

    private static int leadingSpaces(String line) {
        int count = 0;
        for (char c : line.toCharArray()) {
            if (c == ' ') count++;
            else break;
        }
        return count;
    }

    private static String parentPath(String path) {
        int dot = path.lastIndexOf('.');
        return dot < 0 ? "" : path.substring(0, dot);
    }

    private static int findPathInsertPoint(List<String> lines, String path) {
        int start = findPathStart(lines, path);
        if (start < 0) return lines.size();
        int parentIndent = leadingSpaces(lines.get(start));
        int lastContent = start;
        for (int i = start + 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isBlank() || line.stripLeading().startsWith("#")) continue;
            int indent = leadingSpaces(line);
            if (indent <= parentIndent) break;
            lastContent = i;
        }
        return lastContent + 1;
    }

    private static String extractPathBlock(List<String> lines, String path) {
        int start = findPathStart(lines, path);
        if (start < 0) return "";
        int indent = leadingSpaces(lines.get(start));
        int end = start + 1;
        for (int i = start + 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (!line.isBlank() && !line.stripLeading().startsWith("#") && leadingSpaces(line) <= indent) break;
            end = i + 1;
        }
        return String.join("\n", lines.subList(start, end));
    }

    private static int findPathStart(List<String> lines, String path) {
        String[] expected = path.split("\\.");
        String[] stack = new String[expected.length];
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isBlank() || line.stripLeading().startsWith("#")) continue;
            int indent = leadingSpaces(line);
            if (indent % 2 != 0) continue;
            int level = indent / 2;
            if (level >= stack.length) continue;
            String trimmed = line.substring(indent);
            int colon = trimmed.indexOf(':');
            if (colon <= 0) continue;
            stack[level] = trimmed.substring(0, colon).trim();
            Arrays.fill(stack, level + 1, stack.length, null);
            if (level == expected.length - 1 && Arrays.equals(stack, expected)) return i;
        }
        return -1;
    }
}
