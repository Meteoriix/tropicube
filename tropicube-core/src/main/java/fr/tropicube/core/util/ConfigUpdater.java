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
 * Merges the new keys of an embedded resource with the file on disk,
 * without changing existing values or removing user comments.
 * <p>
 * {@code YamlConfiguration} detects missing paths, then inserts
 * text returns them to their section while retaining the formatting.
 */
public final class ConfigUpdater {

    private ConfigUpdater() {}

    /**
     * Adds resource keys missing from {@code diskFile}.
     * Existing content is never modified.
     *
     * @param plugin resource owner plugin
     * @param resourcePath path inside the JAR, for example {@code languages/fr.yml}
     * @param diskFile file to update on disk
     */
    public static void update(Plugin plugin, String resourcePath, File diskFile) throws IOException {
        if (!diskFile.exists()) return;

        InputStream stream = plugin.getResource(resourcePath);
        if (stream == null) return;

        String defaultsText = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        YamlConfiguration defaults = loadYaml(defaultsText);
        YamlConfiguration disk    = YamlConfiguration.loadConfiguration(diskFile);

        // Lists the sheets present by default but absent from the disk.
        List<String> missing = new ArrayList<>();
        for (String key : defaults.getKeys(true)) {
            if (!defaults.isConfigurationSection(key) && !disk.isSet(key)) {
                missing.add(key);
            }
        }
        if (missing.isEmpty()) return;

        plugin.getLogger().info("[ConfigUpdater] " + diskFile.getName()
                + ": inserting " + missing.size() + " missing key(s).");

        // Works on raw text to preserve comments and key order.
        String diskRaw = Files.readString(diskFile.toPath(), StandardCharsets.UTF_8)
                              .replace("\r\n", "\n").replace('\r', '\n');
        List<String> lines   = new ArrayList<>(Arrays.asList(diskRaw.split("\n", -1)));
        List<String> defLines = Arrays.asList(defaultsText.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1));

        // Groups keys by section; a key without a point is a root scalar.
        Map<String, List<String>> bySection = new LinkedHashMap<>();
        for (String key : missing) {
            int dot = key.indexOf('.');
            String section = dot == -1 ? "\0" + key : key.substring(0, dot);
            bySection.computeIfAbsent(section, k -> new ArrayList<>()).add(key);
        }

        // Prepare the inserts, then apply them from bottom to top.
        List<int[]> insertIndices = new ArrayList<>();
        List<List<String>> insertBlocks = new ArrayList<>();
        Set<String> insertedDeepPaths = new HashSet<>();

        for (Map.Entry<String, List<String>> entry : bySection.entrySet()) {
            String section   = entry.getKey();
            List<String> keys = entry.getValue();

            boolean topLevelScalar = section.startsWith("\0");
            String realSection     = topLevelScalar ? section.substring(1) : section;

            if (topLevelScalar || !disk.isConfigurationSection(realSection)) {
                // Adds an entirely missing section or root value to the end of the file.
                String block = topLevelScalar
                        ? extractTopLevelKeyBlock(defLines, realSection)
                        : extractSectionBlock(defLines, realSection);
                if (block.isEmpty()) continue;

                List<String> blockLines = new ArrayList<>();
                // Adds a separator only if the file does not already have one.
                if (!lines.isEmpty() && !lines.get(lines.size() - 1).isBlank()) {
                    blockLines.add("");
                }
                blockLines.addAll(Arrays.asList(block.split("\n", -1)));
                insertIndices.add(new int[]{lines.size(), insertBlocks.size()});
                insertBlocks.add(blockLines);
            } else {
                // Inserts subkeys after the last content of their section.
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

        // Applies inserts in reverse order to preserve previous indexes.
        insertIndices.sort((a, b) -> b[0] - a[0]);
        for (int[] ip : insertIndices) {
            int at = Math.min(ip[0], lines.size());
            lines.addAll(at, insertBlocks.get(ip[1]));
        }

        Files.writeString(diskFile.toPath(), String.join("\n", lines), StandardCharsets.UTF_8);
        plugin.getLogger().info("[ConfigUpdater] " + diskFile.getName() + " updated successfully.");
    }

        // Parse the YAML as text

    private static YamlConfiguration loadYaml(String text) {
        YamlConfiguration cfg = new YamlConfiguration();
        try { cfg.loadFromString(text); } catch (Exception ignored) {}
        return cfg;
    }

    /**
     * Returns the index following the last indented line of a root section.
     * Blank lines and comments separating two sections are not included.
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
     * Extracts a complete root section and its contiguous comments.
     */
    private static String extractSectionBlock(List<String> defLines, String section) {
        int sectionStart = -1;
        for (int i = 0; i < defLines.size(); i++) {
            if (isSectionHeader(defLines.get(i), section)) { sectionStart = i; break; }
        }
        if (sectionStart == -1) return "";

        // Go back to the adjacent comments, without crossing an empty line.
        int blockStart = sectionStart;
        for (int i = sectionStart - 1; i >= 0; i--) {
            if (defLines.get(i).startsWith("#")) blockStart = i;
            else break;
        }

        // The next root key completes the section.
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
     * Extracts an isolated scalar value from the root.
     */
    private static String extractTopLevelKeyBlock(List<String> defLines, String key) {
        String prefix = key + ":";
        for (int i = 0; i < defLines.size(); i++) {
            String line = defLines.get(i);
            if (line.equals(prefix) || line.startsWith(prefix + " ") || line.startsWith(prefix + "\t")) {
                // Includes previous contiguous comments.
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
     * Extracts a subkey and its contiguous comments in a given section.
     *
     * @param section root section name
     * @param subKey direct name of the subkey, without a dot
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

        // Locates the expected key with two indentation spaces.
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

        // Include previous contiguous comments in the same section.
        int blockStart = keyLine;
        for (int i = keyLine - 1; i > sectionStart; i--) {
            String line = defLines.get(i);
            if (line.trim().startsWith("#")) blockStart = i;
            else break;
        }

        // Includes descendants indented by at least four spaces.
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
