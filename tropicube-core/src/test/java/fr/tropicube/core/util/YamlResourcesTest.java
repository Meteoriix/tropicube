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
import java.util.regex.Pattern;
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
    void translationsKeepTheSamePositionalPlaceholders() {
        assertLanguagePlaceholdersMatch(Path.of("src/main/resources/languages"));
        assertLanguagePlaceholdersMatch(Path.of("../tropicube-velocity/src/main/resources/languages"));
    }

    @Test
    void everyTranslationIsValidMiniMessage() {
        for (Path directory : List.of(
                Path.of("src/main/resources/languages"),
                Path.of("../tropicube-velocity/src/main/resources/languages"))) {
            for (String language : List.of("fr", "en", "es", "de")) {
                Map<String, Object> values = leafValues(directory.resolve(language + ".yml"));
                values.forEach((key, value) -> {
                    List<?> messages = value instanceof List<?> list ? list : List.of(value);
                    for (Object message : messages) {
                        String resolved = String.valueOf(message).replaceAll("\\{\\d+}", "valeur");
                        assertDoesNotThrow(() -> MessageStyle.component(resolved),
                                () -> "MiniMessage invalide pour " + language + ": " + key);
                    }
                });
            }
        }
    }

    @Test
    void legacyDecorativePrefixesAreGone() throws Exception {
        List<Path> roots = List.of(
                Path.of("src/main/resources"),
                Path.of("../tropicube-lobby/src/main/resources"),
                Path.of("../tropicube-sheepwars/src/main/resources"),
                Path.of("../tropicube-velocity/src/main/resources"),
                Path.of("src/main/java"),
                Path.of("../tropicube-lobby/src/main/java"),
                Path.of("../tropicube-sheepwars/src/main/java"),
                Path.of("../tropicube-velocity/src/main/java"),
                Path.of("../dockerfiles/configs")
        );
        for (Path root : roots) {
            try (var paths = Files.walk(root)) {
                for (Path file : paths.filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(".java")
                                || path.toString().endsWith(".yml")
                                || path.toString().endsWith(".yaml"))
                        .toList()) {
                    String content = Files.readString(file);
                    assertFalse(content.contains("[Tropicube"), () -> "Ancien préfixe dans " + file);
                    assertFalse(content.contains("[SW]"), () -> "Ancien préfixe SheepWars dans " + file);
                    assertFalse(content.matches("(?s).*\\[(VIP\\+?|Premium|Joueur|Helper|Modo|Admin|Owner)] .*"),
                            () -> "Ancien grade décoratif dans " + file);
                }
            }
        }
    }

    @Test
    void languageSelectorsDoNotUseRegionalFlagSymbols() throws Exception {
        for (Path root : List.of(Path.of("src/main/resources"),
                Path.of("../tropicube-lobby/src/main/resources"), Path.of("src/main/java"))) {
            try (var paths = Files.walk(root)) {
                for (Path file : paths.filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(".java") || path.toString().endsWith(".yml"))
                        .toList()) {
                    String content = Files.readString(file);
                    for (String flag : List.of("🇫🇷", "🇬🇧", "🇪🇸", "🇩🇪")) {
                        assertFalse(content.contains(flag), () -> "Drapeau régional restant dans " + file);
                    }
                }
            }
        }
    }

    @Test
    void settingsLanguageButtonIsBilingualInEveryLocale() {
        String expected = "<light_purple>🌐 Langues / Language";
        for (String language : List.of("fr", "en", "es", "de")) {
            Map<String, Object> values = leafValues(
                    Path.of("src/main/resources/languages", language + ".yml"));
            assertEquals(expected, values.get("lobby.settings-language-name"));
        }
    }

    @Test
    void paperBackendsDisableAllAdvancements() {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(
                Path.of("../dockerfiles/configs/spigot.yml").toFile());
        assertTrue(config.getBoolean("advancements.disable-saving"));
        assertEquals(List.of("*"), config.getStringList("advancements.disabled"));
    }

    @Test
    void paperBackendsBundleConfigsRequiredForOfflineStartup() throws Exception {
        Path configs = Path.of("../dockerfiles/configs");
        YamlConfiguration bukkit = YamlConfiguration.loadConfiguration(configs.resolve("bukkit.yml").toFile());
        YamlConfiguration worldDefaults = YamlConfiguration.loadConfiguration(
                configs.resolve("paper-world-defaults.yml").toFile());

        assertEquals("permissions.yml", bukkit.getString("settings.permissions-file"));
        assertEquals(31, worldDefaults.getInt("_version"));

        for (String dockerfileName : List.of("Dockerfile.lobby", "Dockerfile.sheepwars")) {
            String dockerfile = Files.readString(Path.of("../dockerfiles", dockerfileName));
            assertTrue(dockerfile.contains("dockerfiles/configs/bukkit.yml"), dockerfileName);
            assertTrue(dockerfile.contains("dockerfiles/configs/paper-world-defaults.yml"), dockerfileName);
        }
    }

    @Test
    void playerDrivenNarrativeMessagesStayUnprefixed() {
        List<String> narrativeKeys = List.of(
                "join.message",
                "join.first-join",
                "join.welcome-back",
                "quit.message",
                "sw.player-joined",
                "sw.player-left"
        );
        for (String language : List.of("fr", "en", "es", "de")) {
            Map<String, Object> values = leafValues(Path.of("src/main/resources/languages", language + ".yml"));
            for (String key : narrativeKeys) {
                String message = String.valueOf(values.get(key));
                assertFalse(message.startsWith("<tc>"), () -> "Préfixe réseau inattendu pour " + key);
                assertFalse(message.startsWith("<sw>"), () -> "Préfixe SheepWars inattendu pour " + key);
            }
        }
    }

    @Test
    void hudUsesLocalizedBrandedTitles() {
        for (String language : List.of("fr", "en", "es", "de")) {
            Map<String, Object> values = leafValues(Path.of("src/main/resources/languages", language + ".yml"));
            assertEquals("<gold><bold>🌴 TROPICUBE</bold></gold>", values.get("lobby.sb-title"));
            assertEquals("<aqua><bold>🐑 SHEEPWARS</bold></aqua>", values.get("sw.sb-title"));
            assertEquals("<dark_aqua>• • • • • • •</dark_aqua>", values.get("lobby.sb-separator"));
            assertEquals("<dark_aqua>• • • • • • •</dark_aqua>", values.get("sw.sb-separator"));
            assertNotNull(values.get("lobby.sb-balance"));
            assertNotNull(values.get("lobby.sb-network-online"));
            assertNotNull(values.get("lobby.sb-games"));
            assertNotNull(values.get("sw.sb-map"));
            assertNotNull(values.get("sw.sb-class"));
            assertTrue(String.valueOf(values.get("lobby.tab-header")).contains("🌴 TROPICUBE"));
            assertTrue(String.valueOf(values.get("sw.tab-header")).contains("🐑 SHEEPWARS"));
        }
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

    private static void assertLanguagePlaceholdersMatch(Path languageDirectory) {
        Map<String, Object> french = leafValues(languageDirectory.resolve("fr.yml"));
        for (String language : List.of("en", "es", "de")) {
            Map<String, Object> translated = leafValues(languageDirectory.resolve(language + ".yml"));
            for (String key : french.keySet()) {
                assertEquals(placeholders(french.get(key)), placeholders(translated.get(key)),
                        "Placeholders différents pour " + language + ": " + key);
            }
        }
    }

    private static Set<String> placeholders(Object value) {
        var matcher = Pattern.compile("\\{\\d+}").matcher(String.valueOf(value));
        Set<String> result = new HashSet<>();
        while (matcher.find()) result.add(matcher.group());
        return result;
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
