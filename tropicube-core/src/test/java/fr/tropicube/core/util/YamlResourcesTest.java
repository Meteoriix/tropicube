package fr.tropicube.core.util;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.bukkit.plugin.Plugin;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class YamlResourcesTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void allProjectYamlFilesAreValid() throws Exception {
        List<Path> roots = List.of(
                Path.of("src/main/resources"),
                Path.of("../tropicube-lobby/src/main/resources"),
                Path.of("../tropicube-sheepwars/src/main/resources"),
                Path.of("../tropicube-velocity/src/main/resources"),
                Path.of("../dockerfiles/configs")
        );
        List<Path> files = new ArrayList<>();
        for (Path root : roots) {
            try (var paths = Files.walk(root)) {
                paths.filter(p -> p.toString().endsWith(".yml")).forEach(files::add);
            }
        }
        assertFalse(files.isEmpty());
        for (Path file : files) {
            String yaml = Files.readString(file);
            if (file.toString().contains("languages")) {
                assertNoDuplicateLanguageKeys(file, yaml);
            }
            assertDoesNotThrow(() -> {
                YamlConfiguration config = new YamlConfiguration();
                config.loadFromString(yaml);
            }, () -> "YAML invalide : " + file);
        }
    }

    @Test
    void bundledLanguagesExposeTheSameLeafKeys() {
        Path languageDirectory = Path.of("src/main/resources/languages");
        Set<String> expected = leafKeys(languageDirectory.resolve("fr.yml"));
        for (String language : List.of("en", "es", "de"))
            assertEquals(expected, leafKeys(languageDirectory.resolve(language + ".yml")),
                    "Clés de traduction différentes pour " + language);
    }

    @Test
    void deployedLanguagesMatchBundledLanguageKeys() {
        assertLanguageKeysMatch(
                Path.of("src/main/resources/languages"),
                Path.of("../dockerfiles/configs/TropicubeCore/languages"));
        assertLanguageKeysMatch(
                Path.of("../tropicube-velocity/src/main/resources/languages"),
                Path.of("../dockerfiles/configs/TropicubeVelocity/languages"));
    }

    private static void assertLanguageKeysMatch(Path bundled, Path deployed) {
        for (String language : List.of("fr", "en", "es", "de")) {
            Path bundledFile = bundled.resolve(language + ".yml");
            Path deployedFile = deployed.resolve(language + ".yml");
            assertEquals(leafKeys(bundledFile), leafKeys(deployedFile),
                    "Clés déployées différentes pour " + language);
            Map<String, Object> bundledValues = leafValues(bundledFile);
            Map<String, Object> deployedValues = leafValues(deployedFile);
            for (String key : bundledValues.keySet()) {
                assertEquals(bundledValues.get(key), deployedValues.get(key),
                        "Traduction déployée différente pour " + language + ": " + key);
            }
        }
    }

    @Test
    void configUpdaterAddsDeepKeysWithoutOverwritingExistingValues() throws Exception {
        String defaults = """
                custom:
                  existing: default
                  privacy:
                    private: Private
                    public: Public
                """;
        Path disk = temporaryDirectory.resolve("config.yml");
        Files.writeString(disk, """
                custom:
                  existing: user-value
                """);

        Plugin plugin = (Plugin) Proxy.newProxyInstance(Plugin.class.getClassLoader(),
                new Class<?>[]{Plugin.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getResource" -> new ByteArrayInputStream(defaults.getBytes(StandardCharsets.UTF_8));
                    case "getLogger" -> Logger.getAnonymousLogger();
                    default -> null;
                });

        ConfigUpdater.update(plugin, "config.yml", disk.toFile());
        YamlConfiguration updated = YamlConfiguration.loadConfiguration(disk.toFile());
        assertEquals("user-value", updated.getString("custom.existing"));
        assertEquals("Private", updated.getString("custom.privacy.private"));
        assertEquals("Public", updated.getString("custom.privacy.public"));
    }

    private static Set<String> leafKeys(Path file) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file.toFile());
        Set<String> result = new HashSet<>();
        for (String key : config.getKeys(true))
            if (!config.isConfigurationSection(key)) result.add(key);
        return result;
    }

    private static Map<String, Object> leafValues(Path file) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file.toFile());
        Map<String, Object> result = new TreeMap<>();
        for (String key : config.getKeys(true)) {
            if (!config.isConfigurationSection(key)) result.put(key, config.get(key));
        }
        return result;
    }

    private static void assertNoDuplicateLanguageKeys(Path file, String yaml) {
        Set<String> keys = new HashSet<>();
        String section = null;
        for (String line : yaml.lines().toList()) {
            if (line.matches("^[\\w-]+:\\s*$")) {
                section = line.substring(0, line.indexOf(':'));
            } else if (section != null && line.matches("^  [\\w-]+:\\s.*$")) {
                String leaf = line.substring(2, line.indexOf(':'));
                String fullKey = section + "." + leaf;
                assertTrue(keys.add(fullKey),
                        () -> "Clé YAML dupliquée dans " + file + " : " + fullKey);
            }
        }
    }
}
