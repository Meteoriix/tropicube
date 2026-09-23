package fr.tropicube.core.ui;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class RuntimeUiBundleTest {
    @TempDir
    Path directory;

    @Test
    void olderRedisCatalogKeepsCustomizationsAndEveryBundledKeyAfterRepeatedRestore() throws Exception {
        Plugin plugin = (Plugin) Proxy.newProxyInstance(Plugin.class.getClassLoader(),
                new Class<?>[]{Plugin.class}, (proxy, method, arguments) -> switch (method.getName()) {
                    case "getResource" -> new ByteArrayInputStream(Files.readAllBytes(
                            Path.of("src/main/resources", (String) arguments[0])));
                    case "getLogger" -> Logger.getAnonymousLogger();
                    default -> null;
                });
        for (String language : List.of("fr", "en", "de", "es")) {
            Path target = directory.resolve(language + ".yml");
            Path bundled = Path.of("src/main/resources/languages", language + ".yml");
            // Startup has already merged the current JAR before the older Redis generation arrives.
            Files.copy(bundled, target);
            byte[] previousGeneration = "# Custom text\nsocial:\n  menu-title: Custom social\n"
                    .getBytes(StandardCharsets.UTF_8);
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(bundled.toFile());
            for (int restore = 0; restore < 2; restore++) {
                RuntimeUiBundle.installFile(plugin, "core/languages/" + language + ".yml",
                        target, previousGeneration);
                YamlConfiguration actual = YamlConfiguration.loadConfiguration(target.toFile());
                assertEquals("Custom social", actual.getString("social.menu-title"));
                assertTrue(Files.readString(target).startsWith("# Custom text\n"));
                for (String key : defaults.getKeys(true)) {
                    if (defaults.isConfigurationSection(key) || key.equals("social.menu-title")) continue;
                    assertEquals(defaults.get(key), actual.get(key), language + ": " + key);
                }
            }
        }
        try (var files = Files.list(directory)) {
            assertEquals(4, files.count(), "Temporary restore files must be removed");
        }
    }

    @Test
    void uiManifestsRemainByteForByteIdentical() throws Exception {
        byte[] content = "version: 1\nmenus: {}\n".getBytes(StandardCharsets.UTF_8);
        Path target = directory.resolve("menus.yml");
        RuntimeUiBundle.installFile(null, "core/menus.yml", target, content);
        assertArrayEquals(content, Files.readAllBytes(target));
    }

    @Test
    void formerBundledNavigationTextsAreMigratedWithoutReplacingCustomValues() throws Exception {
        Plugin plugin = resourcePlugin();
        Map<String, List<String>> formerValues = Map.of(
                "fr", List.of("<aqua>⚡ Sélecteur de serveur", "<yellow>Clic gauche : choisir le type de partie", "<aqua>☀ Social"),
                "en", List.of("<aqua>⚡ Server Selector", "<yellow>Left click: choose a match type", "<aqua>☀ Social"),
                "de", List.of("<aqua>⚡ Serverauswahl", "<yellow>Linksklick: Art der Partie wählen", "<aqua>☀ Sozial"),
                "es", List.of("<aqua>⚡ Selector de servidor", "<yellow>Clic izquierdo: elegir el tipo de partida", "<aqua>☀ Social"));

        for (String language : List.of("fr", "en", "de", "es")) {
            Path bundled = Path.of("src/main/resources/languages", language + ".yml");
            YamlConfiguration previous = YamlConfiguration.loadConfiguration(bundled.toFile());
            previous.set("lobby.hotbar-servers-name", formerValues.get(language).get(0));
            previous.set("lobby.type-lore-left-click", formerValues.get(language).get(1));
            previous.set("social.hotbar-name", formerValues.get(language).get(2));
            previous.set("social.menu-title", "Custom social");
            Path target = directory.resolve("migrated-" + language + ".yml");

            RuntimeUiBundle.installFile(plugin, "core/languages/" + language + ".yml", target,
                    previous.saveToString().getBytes(StandardCharsets.UTF_8));

            YamlConfiguration actual = YamlConfiguration.loadConfiguration(target.toFile());
            YamlConfiguration current = YamlConfiguration.loadConfiguration(bundled.toFile());
            assertEquals(current.getString("lobby.hotbar-servers-name"), actual.getString("lobby.hotbar-servers-name"));
            assertEquals(current.getString("lobby.type-lore-left-click"), actual.getString("lobby.type-lore-left-click"));
            assertEquals(current.getString("social.hotbar-name"), actual.getString("social.hotbar-name"));
            assertEquals("Custom social", actual.getString("social.menu-title"));
        }

        Path customized = directory.resolve("customized-fr.yml");
        YamlConfiguration custom = YamlConfiguration.loadConfiguration(
                Path.of("src/main/resources/languages/fr.yml").toFile());
        custom.set("lobby.hotbar-servers-name", "<blue>Mes jeux");
        custom.set("lobby.type-lore-left-click", formerValues.get("fr").get(1));
        RuntimeUiBundle.installFile(plugin, "core/languages/fr.yml", customized,
                custom.saveToString().getBytes(StandardCharsets.UTF_8));
        YamlConfiguration actual = YamlConfiguration.loadConfiguration(customized.toFile());
        assertEquals("<blue>Mes jeux", actual.getString("lobby.hotbar-servers-name"));
        assertEquals("<green>▶ Clic gauche : file Quick Play.", actual.getString("lobby.type-lore-left-click"));
    }

    @Test
    void failedCatalogMergePreservesInstalledFileAndCleansTemporaryFile() throws Exception {
        Path target = directory.resolve("fr.yml");
        Files.writeString(target, "social:\n  menu-title: Installed\n");
        byte[] installed = Files.readAllBytes(target);
        Plugin plugin = (Plugin) Proxy.newProxyInstance(Plugin.class.getClassLoader(),
                new Class<?>[]{Plugin.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("getResource")) return new InputStream() {
                        @Override public int read() throws IOException { throw new IOException("Resource unavailable"); }
                    };
                    return null;
                });
        assertThrows(IOException.class, () -> RuntimeUiBundle.installFile(plugin,
                "core/languages/fr.yml", target, "social: {}\n".getBytes(StandardCharsets.UTF_8)));
        assertArrayEquals(installed, Files.readAllBytes(target));
        try (var files = Files.list(directory)) {
            assertEquals(1, files.count());
        }
    }

    private static Plugin resourcePlugin() {
        return (Plugin) Proxy.newProxyInstance(Plugin.class.getClassLoader(),
                new Class<?>[]{Plugin.class}, (proxy, method, arguments) -> switch (method.getName()) {
                    case "getResource" -> new ByteArrayInputStream(Files.readAllBytes(
                            Path.of("src/main/resources", (String) arguments[0])));
                    case "getLogger" -> Logger.getAnonymousLogger();
                    default -> null;
                });
    }
}
