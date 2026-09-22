package fr.tropicube.core.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.tropicube.docker.model.AccessPolicy;
import fr.tropicube.docker.model.PlayerAccessProfile;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.bukkit.plugin.Plugin;
import org.bukkit.Material;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class YamlResourcesTest {
    private static final List<String> LANGUAGES = List.of("fr", "en", "es", "de");
    private static final TagResolver STANDARD_MINI_MESSAGE_TAGS = StandardTags.defaults();
    private static final Set<String> PROJECT_MINI_MESSAGE_TAGS = Set.of("sw", "tc");
    private static final Pattern MINI_MESSAGE_TAG = Pattern.compile("(?<!\\\\)<([^<>]+)>");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(?:\\d+|[a-z][a-z0-9_]*)}");
    private static final Pattern NAMED_PLACEHOLDER = Pattern.compile("\\{([a-z][a-z0-9_]*)}");
    private static final Pattern POSITIONAL_PLACEHOLDER = Pattern.compile("\\{\\d+}");

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
    void accessThresholdsRemainReadableWithDottedPermissionNames() {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(
                Path.of("src/main/resources/config.yml").toFile());
        ConfigurationSection section = config.getConfigurationSection("access.permission-thresholds.vip");
        assertNotNull(section);
        Map<String, Integer> thresholds = new TreeMap<>();
        section.getValues(true).forEach((permission, value) -> {
            if (value instanceof Number number) thresholds.put(permission, number.intValue());
        });

        AccessPolicy policy = new AccessPolicy(thresholds, Map.of());
        assertTrue(policy.hasPermission(new PlayerAccessProfile(2, 0, 0), "tropicube.queue.priority"));
    }

    @Test
    void bundledLanguagesExposeTheSameLeafKeys() {
        for (Path languageDirectory : List.of(
                Path.of("src/main/resources/languages"),
                Path.of("../tropicube-velocity/src/main/resources/languages"))) {
            Set<String> expected = leafKeys(languageDirectory.resolve("fr.yml"));
            for (String language : List.of("en", "es", "de")) {
                assertEquals(expected, leafKeys(languageDirectory.resolve(language + ".yml")),
                        "Clés de traduction différentes pour " + languageDirectory + "/" + language);
            }
        }
    }

    @Test
    void translationsKeepTheSamePlaceholders() {
        assertLanguagePlaceholdersMatch(Path.of("src/main/resources/languages"));
        assertLanguagePlaceholdersMatch(Path.of("../tropicube-velocity/src/main/resources/languages"));
    }

    @Test
    void bundledLanguagesOnlyUseNamedPlaceholders() throws Exception {
        for (Path directory : List.of(
                Path.of("src/main/resources/languages"),
                Path.of("../tropicube-velocity/src/main/resources/languages"))) {
            for (String language : LANGUAGES) {
                Path file = directory.resolve(language + ".yml");
                assertFalse(POSITIONAL_PLACEHOLDER.matcher(Files.readString(file)).find(),
                        () -> "Placeholder positionnel restant dans " + file);
            }
        }
    }

    @Test
    void bundledLanguagesUseTheCanonicalPlaceholderCatalog() throws Exception {
        Set<String> placeholders = new HashSet<>();
        for (Path file : List.of(
                Path.of("src/main/resources/languages/fr.yml"),
                Path.of("../tropicube-velocity/src/main/resources/languages/fr.yml"))) {
            var matcher = NAMED_PLACEHOLDER.matcher(Files.readString(file));
            while (matcher.find()) placeholders.add(matcher.group(1));
        }

        assertEquals(158, placeholders.size(),
                "Le catalogue canonique doit évoluer explicitement avec les descriptions de l’éditeur");
        assertTrue(Set.of(
                "friend_accepted",
                "vip_bought",
                "admin_info_container",
                "tab_footer_waiting",
                "tab_footer_starting",
                "capacity",
                "friends"
        ).stream().noneMatch(placeholders::contains),
                "Les anciens alias ne doivent pas être réintroduits");
    }

    @Test
    void uiManifestsReferenceExistingTranslationsAndValidMaterials() {
        Map<String, Object> translations = leafValues(Path.of("src/main/resources/languages/fr.yml"));
        for (Path manifest : List.of(Path.of("src/main/resources/menus.yml"),
                Path.of("../tropicube-lobby/src/main/resources/menus.yml"),
                Path.of("../tropicube-sheepwars/src/main/resources/menus.yml"),
                Path.of("../tropicube-fallenkingdoms/src/main/resources/menus.yml"))) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(manifest.toFile());
            ConfigurationSection menus = yaml.getConfigurationSection("menus");
            assertNotNull(menus);
            for (String menuId : menus.getKeys(false)) {
                ConfigurationSection menu = menus.getConfigurationSection(menuId);
                assertNotNull(menu);
                assertTranslationKey(translations, manifest, menu.getString("title-key"));
                ConfigurationSection buttons = menu.getConfigurationSection("buttons");
                if (buttons == null) continue;
                for (String buttonId : buttons.getKeys(false)) {
                    ConfigurationSection button = buttons.getConfigurationSection(buttonId);
                    assertNotNull(button);
                    assertNotNull(Material.matchMaterial(button.getString("material", "")),
                            () -> manifest + ": matériau invalide pour " + menuId + "." + buttonId);
                    assertTranslationKey(translations, manifest, button.getString("name-key"));
                    assertTranslationKey(translations, manifest, button.getString("lore-key"));
                }
            }
        }
        for (Path manifest : List.of(Path.of("../tropicube-lobby/src/main/resources/scoreboards.yml"),
                Path.of("../tropicube-sheepwars/src/main/resources/scoreboards.yml"),
                Path.of("../tropicube-fallenkingdoms/src/main/resources/scoreboards.yml"))) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(manifest.toFile());
            ConfigurationSection scoreboards = yaml.getConfigurationSection("scoreboards");
            assertNotNull(scoreboards);
            for (String id : scoreboards.getKeys(false)) {
                ConfigurationSection scoreboard = scoreboards.getConfigurationSection(id);
                assertNotNull(scoreboard);
                assertTranslationKey(translations, manifest, scoreboard.getString("title-key"));
                ConfigurationSection variants = scoreboard.getConfigurationSection("variants");
                assertNotNull(variants);
                for (String variant : variants.getKeys(false)) for (Map<?, ?> line : variants.getMapList(variant + ".lines")) {
                    if (line.get("key") != null) assertTranslationKey(translations, manifest, String.valueOf(line.get("key")));
                }
            }
        }
        for (Path manifest : List.of(Path.of("../tropicube-lobby/src/main/resources/tablists.yml"),
                Path.of("../tropicube-sheepwars/src/main/resources/tablists.yml"),
                Path.of("../tropicube-fallenkingdoms/src/main/resources/tablists.yml"))) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(manifest.toFile());
            ConfigurationSection tablists = yaml.getConfigurationSection("tablists");
            assertNotNull(tablists);
            for (String id : tablists.getKeys(false)) {
                ConfigurationSection variants = tablists.getConfigurationSection(id + ".variants");
                assertNotNull(variants);
                for (String variant : variants.getKeys(false)) {
                    assertTranslationKey(translations, manifest, variants.getString(variant + ".header-key"));
                    assertTranslationKey(translations, manifest, variants.getString(variant + ".footer-key"));
                }
            }
        }
    }

    private static void assertTranslationKey(Map<String, Object> translations, Path manifest, String key) {
        if (key != null && !key.isBlank()) assertTrue(translations.containsKey(key),
                () -> manifest + ": clé de traduction inconnue " + key);
    }

    @Test
    void translationsKeepTheSameMiniMessagePalette() {
        List<String> differences = new ArrayList<>();
        for (Path directory : List.of(
                Path.of("src/main/resources/languages"),
                Path.of("../tropicube-velocity/src/main/resources/languages"))) {
            Map<String, Object> french = leafValues(directory.resolve("fr.yml"));
            for (String language : List.of("en", "es", "de")) {
                Map<String, Object> translated = leafValues(directory.resolve(language + ".yml"));
                for (String key : french.keySet()) {
                    Set<String> expected = new HashSet<>(formattingTags(french.get(key)));
                    Set<String> actual = new HashSet<>(formattingTags(translated.get(key)));
                    if (!expected.equals(actual)) {
                        differences.add(directory + "/" + language + ": " + key
                                + " attendu=" + expected + " reçu=" + actual);
                    }
                }
            }
        }
        assertTrue(differences.isEmpty(), () -> "Palette MiniMessage différente :\n"
                + String.join("\n", differences));
    }

    @Test
    void everyTranslationIsValidMiniMessage() {
        for (Path directory : List.of(
                Path.of("src/main/resources/languages"),
                Path.of("../tropicube-velocity/src/main/resources/languages"))) {
            for (String language : LANGUAGES) {
                Map<String, Object> values = leafValues(directory.resolve(language + ".yml"));
                values.forEach((key, value) -> {
                    List<?> messages = value instanceof List<?> list ? list : List.of(value);
                    for (Object message : messages) {
                        String resolved = String.valueOf(message)
                                .replaceAll("\\{(?:\\d+|[a-z][a-z0-9_]*)}", "valeur");
                        assertDoesNotThrow(() -> MessageStyle.component(resolved),
                                () -> "MiniMessage invalide pour " + language + ": " + key);
                    }
                });
            }
        }
    }

    @Test
    void translationsOnlyContainKnownUnescapedMiniMessageTags() {
        for (Path directory : List.of(
                Path.of("src/main/resources/languages"),
                Path.of("../tropicube-velocity/src/main/resources/languages"))) {
            for (String language : LANGUAGES) {
                leafValues(directory.resolve(language + ".yml")).forEach((key, value) -> {
                    var matcher = MINI_MESSAGE_TAG.matcher(String.valueOf(value));
                    while (matcher.find()) {
                        String token = matcher.group(1);
                        String name = token.startsWith("/") ? token.substring(1) : token;
                        int argumentSeparator = name.indexOf(':');
                        if (argumentSeparator >= 0) name = name.substring(0, argumentSeparator);
                        String normalizedName = name.toLowerCase(Locale.ROOT);
                        assertTrue(STANDARD_MINI_MESSAGE_TAGS.has(normalizedName)
                                        || PROJECT_MINI_MESSAGE_TAGS.contains(normalizedName),
                                () -> "Balise MiniMessage inconnue ou littéral non échappé pour "
                                        + language + ": " + key + " (<" + token + ">)");
                    }
                });
            }
        }
    }

    @Test
    void playerFacingSourcesDoNotUseLegacySectionColors() throws Exception {
        for (Path root : List.of(
                Path.of("src/main"),
                Path.of("../tropicube-lobby/src/main"),
                Path.of("../tropicube-sheepwars/src/main"),
                Path.of("../tropicube-velocity/src/main"),
                Path.of("../dockerfiles/configs"))) {
            try (var paths = Files.walk(root)) {
                for (Path file : paths.filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(".java")
                                || path.toString().endsWith(".yml")
                                || path.toString().endsWith(".yaml"))
                        .toList()) {
                    assertFalse(Files.readString(file).contains("§"),
                            () -> "Code couleur hérité dans " + file);
                }
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
    void settingsLanguageButtonIsLocalizedInEveryLocale() {
        Map<String, String> expected = Map.of(
                "fr", "<light_purple>🌐 Langue de l'interface",
                "en", "<light_purple>🌐 Interface language",
                "de", "<light_purple>🌐 Sprache der Oberfläche",
                "es", "<light_purple>🌐 Idioma de la interfaz");
        for (String language : List.of("fr", "en", "es", "de")) {
            Map<String, Object> values = leafValues(
                    Path.of("src/main/resources/languages", language + ".yml"));
            assertEquals(expected.get(language), values.get("lobby.settings-language-name"));
        }
    }

    @Test
    void lobbyHotbarAndQuickPlayActionUseTheExpectedLocalizedLabels() {
        Map<String, String> games = Map.of(
                "fr", "<green>⚡ Jeux", "en", "<green>⚡ Games",
                "de", "<green>⚡ Spiele", "es", "<green>⚡ Juegos");
        Map<String, String> quickPlay = Map.of(
                "fr", "<green>▶ Clic gauche : file Quick Play.",
                "en", "<green>▶ Left click: Quick Play queue.",
                "de", "<green>▶ Linksklick: Quick-Play-Warteschlange.",
                "es", "<green>▶ Clic izquierdo: cola Quick Play.");
        for (String language : LANGUAGES) {
            Map<String, Object> values = leafValues(
                    Path.of("src/main/resources/languages", language + ".yml"));
            assertEquals(games.get(language), values.get("lobby.hotbar-servers-name"));
            assertEquals(quickPlay.get(language), values.get("lobby.type-lore-left-click"));
            assertTrue(String.valueOf(values.get("social.hotbar-name")).startsWith("<green>"),
                    () -> "Le bouton Social doit être vert en " + language);
        }
    }

    @Test
    void gameSelectorUsesOnlyTheNeutralFrame() {
        YamlConfiguration menus = YamlConfiguration.loadConfiguration(
                Path.of("../tropicube-lobby/src/main/resources/menus.yml").toFile());

        assertEquals("neutral", menus.getString("menus.server-type-selector.frame"));
    }

    @Test
    void profileAndSocialMenusExposeExplicitLocalizedActions() {
        List<String> keys = List.of(
                "center.profile-action", "center.missions-action", "center.notifications-action",
                "center.guilds-action", "center.privacy-action", "center.profile-title-action",
                "social.party-request-left-click", "social.party-request-right-click",
                "social.party-request-sent-right-click", "lobby.custom-game-type-click");
        for (String language : LANGUAGES) {
            Map<String, Object> values = leafValues(
                    Path.of("src/main/resources/languages", language + ".yml"));
            for (String key : keys) {
                assertTrue(values.containsKey(key), () -> key + " manquant en " + language);
                assertTrue(String.valueOf(values.get(key)).contains(":"),
                        () -> "Action de clic imprécise dans " + key + " en " + language);
            }
        }
        Map<String, Object> french = leafValues(Path.of("src/main/resources/languages/fr.yml"));
        assertEquals("<green>▶ Clic gauche : sélectionner ce type et choisir un jeu",
                french.get("lobby.custom-game-type-click"));
        assertEquals("<dark_aqua>Social <dark_gray>• Amis", french.get("social.menu-title"));
    }

    @Test
    void sharedPlayerHeadsUseAnExplicitDisplayName() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/fr/tropicube/core/menu/NetworkMenuStyle.java"));
        assertTrue(source.contains("meta.displayName(clean(name))"));
        assertFalse(source.contains("meta.itemName(clean(name))"));
        assertTrue(source.contains("playerHead(Player player, Component name"));
    }

    @Test
    void menuDomainValuesHaveLocalizedLabelsInEveryLocale() {
        List<String> keys = List.of(
                "lobby.settings-value-summary", "lobby.settings-value-friends",
                "lobby.settings-value-private", "lobby.settings-value-everyone",
                "lobby.settings-value-friends-party", "lobby.settings-value-nobody",
                "lobby.settings-value-party", "lobby.game-type-sheepwars",
                "center.mission-event-match-played", "center.mission-event-match-won",
                "center.mission-event-player-kill", "center.mission-event-sheep-launched",
                "center.mission-event-damage-dealt", "center.mission-event-party-match",
                "center.mission-event-match-survived", "sw.team-name-red", "sw.team-name-blue");
        for (String language : List.of("fr", "en", "es", "de")) {
            Map<String, Object> values = leafValues(
                    Path.of("src/main/resources/languages", language + ".yml"));
            for (String key : keys) {
                assertTrue(values.containsKey(key), () -> key + " manquant en " + language);
                String visibleText = PLACEHOLDER.matcher(String.valueOf(values.get(key))).replaceAll("");
                assertFalse(visibleText.contains("_"),
                        () -> "Identifiant brut dans " + key + " en " + language);
            }
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
    void velocityPartyDisconnectGraceMatchesDeploymentCopy() {
        YamlConfiguration bundled = YamlConfiguration.loadConfiguration(
                Path.of("../tropicube-velocity/src/main/resources/config.yml").toFile());
        YamlConfiguration deployed = YamlConfiguration.loadConfiguration(
                Path.of("../dockerfiles/configs/TropicubeVelocity/config.yml").toFile());

        assertEquals(60, bundled.getInt("party.disconnect-grace-seconds"));
        assertEquals(bundled.getInt("party.disconnect-grace-seconds"),
                deployed.getInt("party.disconnect-grace-seconds"));
    }

    @Test
    void bedrockBridgeUsesFloodgateAndPinnedProxyArtifacts() throws Exception {
        Path configs = Path.of("../dockerfiles/configs");
        YamlConfiguration geyser = YamlConfiguration.loadConfiguration(
                configs.resolve("Geyser-Velocity/config.yml").toFile());
        YamlConfiguration floodgate = YamlConfiguration.loadConfiguration(
                configs.resolve("floodgate/config.yml").toFile());

        assertEquals("0.0.0.0", geyser.getString("bedrock.address"));
        assertEquals("${CFG_BEDROCK_PORT}", geyser.getString("bedrock.port"));
        assertEquals("${CFG_BEDROCK_PORT}", geyser.getString("advanced.bedrock.broadcast-port"));
        assertEquals("floodgate", geyser.getString("java.auth-type"));
        assertTrue(geyser.getBoolean("advanced.java.use-direct-connection"));
        assertTrue(geyser.getBoolean("gameplay.disable-bedrock-scaffolding"));
        assertTrue(geyser.getBoolean("gameplay.enable-custom-content"));
        assertTrue(geyser.getBoolean("gameplay.enable-integrated-pack"));
        assertTrue(geyser.getBoolean("gameplay.force-resource-packs"));
        assertEquals(7, geyser.getInt("config-version"));

        JsonObject customHeads = JsonParser.parseString(Files.readString(
                configs.resolve("Geyser-Velocity/custom_mappings/tropicube-heads.json"))).getAsJsonObject();
        assertEquals(1, customHeads.get("format_version").getAsInt());
        assertEquals(11, customHeads.getAsJsonObject("skulls").getAsJsonArray("skin_hash").size());

        assertEquals(".", floodgate.getString("username-prefix"));
        assertEquals("key.pem", floodgate.getString("key-file-name"));
        assertFalse(floodgate.getBoolean("player-link.require-link"));
        assertEquals(3, floodgate.getInt("config-version"));

        String dockerfile = Files.readString(Path.of("../dockerfiles/Dockerfile.velocity"));
        assertTrue(dockerfile.contains("versions/2.11.3/builds/1245/downloads/velocity"));
        assertTrue(dockerfile.contains("sha256:306abe38291d24e874c7286e1c04b537ac3ab5e3901b710f56bd242500bedfbf"));
        assertTrue(dockerfile.contains("versions/2.2.5/builds/140/downloads/velocity"));
        assertTrue(dockerfile.contains("sha256:f5867ad79b90d38abcc72755a685428fbcf423b52c9830a39ffed5203de6936a"));

        String compose = Files.readString(Path.of("../docker-compose.yml"));
        assertTrue(compose.contains("${BEDROCK_PORT:-19132}:${BEDROCK_PORT:-19132}/udp"));
        assertTrue(compose.contains("floodgate-data:/server/plugins/floodgate"));

        String entrypoint = Files.readString(Path.of("../dockerfiles/velocity-entrypoint.sh"));
        assertTrue(entrypoint.contains("s/\\${CFG_BEDROCK_PORT}/$CFG_BEDROCK_PORT/g"));
    }

    @Test
    void playerDrivenNarrativeMessagesStayUnprefixed() {
        List<String> narrativeKeys = List.of(
                "join.message",
                "join.first-join",
                "join.welcome-back",
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
            assertNotNull(values.get("lobby.sb-separator"));
            assertNotNull(values.get("sw.sb-separator"));
            assertNotNull(values.get("lobby.sb-balance"));
            assertNotNull(values.get("lobby.sb-network-online"));
            assertNotNull(values.get("lobby.sb-games"));
            assertNotNull(values.get("sw.sb-map"));
            assertNotNull(values.get("sw.sb-class"));
            assertTrue(String.valueOf(values.get("lobby.tab-header")).contains("🌴 TROPICUBE"));
            assertTrue(String.valueOf(values.get("sw.tab-header")).contains("🌴 TROPICUBE"));
            assertTrue(String.valueOf(values.get("sw.tab-header")).contains("SheepWars"));
            assertTrue(String.valueOf(values.get("fk.tab-header")).contains("🏰 FALLEN KINGDOMS"));
        }
    }

    @Test
    void deployedLanguagesMatchBundledFiles() {
        assertLanguagesMatch(
                Path.of("src/main/resources/languages"),
                Path.of("../dockerfiles/configs/TropicubeCore/languages"));
        assertLanguagesMatch(
                Path.of("../tropicube-velocity/src/main/resources/languages"),
                Path.of("../dockerfiles/configs/TropicubeVelocity/languages"));
    }

    @Test
    void deployedTablistsMatchBundledFiles() {
        for (String module : List.of("Lobby", "Sheepwars")) {
            Path bundled = Path.of("../tropicube-" + module.toLowerCase(Locale.ROOT)
                    + "/src/main/resources/tablists.yml");
            Path deployed = Path.of("../dockerfiles/configs/Tropicube" + module + "/tablists.yml");
            assertDoesNotThrow(() -> assertEquals(-1L, Files.mismatch(bundled, deployed),
                    () -> "Copie Docker différente pour " + module + "/tablists.yml"));
        }
    }

    @Test
    void localizedHelpCatalogCoversEveryRegisteredCommandFamily() {
        List<String> categories = List.of("general", "games", "social", "profile", "staff");
        List<String> commands = List.of(
                "help", "lobby", "spawn", "lang", "languages", "money", "vip",
                "msg", "reply", "ignore", "globalchat", "report",
                "play", "quickplay", "competitive", "server", "queue", "replay",
                "replayconfirm", "rejoin", "whitelist", "sheepwars",
                "friend", "party", "pc", "guild", "profile", "center", "missions",
                "notifications", "settings", "nick", "fly", "2fa", "staff", "staffchat",
                "reports", "kick", "mute", "unmute", "warn", "history", "ban", "tempban",
                "unban", "privacy", "find", "send", "pull", "maintenance", "announce",
                "networkdiag", "eco", "rank", "level", "tropicube", "coreadmin");

        for (String language : List.of("fr", "en", "es", "de")) {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(
                    Path.of("src/main/resources/languages", language + ".yml").toFile());
            String catalog = config.getString("help.header", "") + categories.stream()
                    .flatMap(category -> config.getStringList("help." + category).stream())
                    .reduce("", (left, right) -> left + '\n' + right);
            for (String command : commands) {
                assertTrue(catalog.contains("/" + command),
                        () -> "Commande /" + command + " absente de l'aide " + language);
            }
        }
    }

    @Test
    void localizedHelpUsesOneDetailedLinePerPrimaryCommand() {
        Map<String, List<String>> commandsByCategory = Map.of(
                "general", List.of("help", "lobby", "spawn", "lang", "languages", "money", "vip",
                        "msg", "reply", "ignore", "globalchat", "report"),
                "games", List.of("play", "quickplay", "competitive", "server", "queue", "replay",
                        "replayconfirm", "rejoin", "whitelist", "sheepwars"),
                "social", List.of("friend", "party", "pc", "guild"),
                "profile", List.of("profile", "center", "missions", "notifications", "settings", "nick", "fly"),
                "staff", List.of("2fa", "staff", "staffchat", "reports", "kick", "mute", "unmute",
                        "warn", "history", "ban", "tempban", "unban", "privacy", "find", "send", "pull",
                        "maintenance", "announce", "networkdiag", "eco", "rank", "level", "tropicube",
                        "coreadmin")
        );
        Map<String, List<String>> aliases = Map.ofEntries(
                Map.entry("lobby", List.of("hub")), Map.entry("lang", List.of("language", "langue")),
                Map.entry("money", List.of("balance")), Map.entry("vip", List.of("boutique", "shop")),
                Map.entry("msg", List.of("tell", "w")), Map.entry("reply", List.of("r")),
                Map.entry("globalchat", List.of("g")), Map.entry("play", List.of("servers", "sv")),
                Map.entry("queue", List.of("file")),
                Map.entry("replay", List.of("playnext", "playagain", "rejouer")),
                Map.entry("sheepwars", List.of("swprofile")),
                Map.entry("friend", List.of("friends", "ami", "amis")),
                Map.entry("party", List.of("groupe")), Map.entry("guild", List.of("guilde")),
                Map.entry("profile", List.of("profil")), Map.entry("center", List.of("centre")),
                Map.entry("notifications", List.of("inbox")),
                Map.entry("settings", List.of("preferences", "parametres")),
                Map.entry("fly", List.of("flymode", "fm")), Map.entry("staffchat", List.of("sc")),
                Map.entry("networkdiag", List.of("netdiag")), Map.entry("rank", List.of("grade")),
                Map.entry("tropicube", List.of("tropi", "cm")),
                Map.entry("coreadmin", List.of("tropiadmin", "ca"))
        );

        for (String language : LANGUAGES) {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(
                    Path.of("src/main/resources/languages", language + ".yml").toFile());
            for (Map.Entry<String, List<String>> category : commandsByCategory.entrySet()) {
                List<String> lines = config.getStringList("help." + category.getKey());
                assertEquals(category.getValue().size(), lines.size(),
                        () -> "Nombre de lignes d'aide incorrect pour " + language + "/" + category.getKey());
                for (String command : category.getValue()) {
                    Pattern primary = Pattern.compile("<(?:aqua|gold)>/" + Pattern.quote(command) + "(?:\\s|<)");
                    List<String> matching = lines.stream().filter(line -> primary.matcher(line).find()).toList();
                    assertEquals(1, matching.size(),
                            () -> "La commande /" + command + " doit avoir exactement une ligne en " + language);
                    String line = matching.getFirst();
                    assertTrue(line.contains(" <dark_gray>") && line.contains("— <gray>"),
                            () -> "Utilisation ou description incomplète pour /" + command + " en " + language);
                    for (String alias : aliases.getOrDefault(command, List.of())) {
                        assertTrue(line.contains("/" + alias),
                                () -> "Alias /" + alias + " absent de la ligne /" + command + " en " + language);
                    }
                }
            }
        }
    }

    private static void assertLanguagesMatch(Path bundled, Path deployed) {
        for (String language : List.of("fr", "en", "es", "de")) {
            Path bundledFile = bundled.resolve(language + ".yml");
            Path deployedFile = deployed.resolve(language + ".yml");
            assertDoesNotThrow(() -> assertEquals(-1L, Files.mismatch(bundledFile, deployedFile),
                    "Le fichier déployé doit être une copie exacte pour " + language));
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

    private static List<String> placeholders(Object value) {
        var matcher = PLACEHOLDER.matcher(String.valueOf(value));
        List<String> result = new ArrayList<>();
        while (matcher.find()) result.add(matcher.group());
        return result;
    }

    private static List<String> formattingTags(Object value) {
        var matcher = MINI_MESSAGE_TAG.matcher(String.valueOf(value));
        List<String> result = new ArrayList<>();
        while (matcher.find()) result.add(matcher.group(1).toLowerCase(Locale.ROOT));
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

    @Test
    void configUpdaterExpandsInlineEmptyMappingBeforeAddingChildren() throws Exception {
        String defaults = """
                menus:
                  map-vote:
                    rows: 6
                    buttons:
                      previous:
                        slot: 45
                        action: previous_page
                    dynamic-regions:
                      maps:
                        preview-count: 5
                """;
        Path disk = temporaryDirectory.resolve("menus.yml");
        Files.writeString(disk, """
                menus:
                  map-vote:
                    rows: 1
                    buttons: {} # No buttons in the former one-row menu.
                    dynamic-regions:
                      maps:
                        preview-count: 3
                """);

        Plugin plugin = (Plugin) Proxy.newProxyInstance(Plugin.class.getClassLoader(),
                new Class<?>[]{Plugin.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getResource" -> new ByteArrayInputStream(defaults.getBytes(StandardCharsets.UTF_8));
                    case "getLogger" -> Logger.getAnonymousLogger();
                    default -> null;
                });

        ConfigUpdater.update(plugin, "menus.yml", disk.toFile());

        YamlConfiguration updated = new YamlConfiguration();
        assertDoesNotThrow(() -> updated.load(disk.toFile()));
        assertEquals(1, updated.getInt("menus.map-vote.rows"));
        assertEquals(45, updated.getInt("menus.map-vote.buttons.previous.slot"));
        assertEquals("previous_page", updated.getString("menus.map-vote.buttons.previous.action"));
        assertEquals(3, updated.getInt("menus.map-vote.dynamic-regions.maps.preview-count"));
        String updatedText = Files.readString(disk);
        assertFalse(updatedText.contains("buttons: {}"));
        assertTrue(updatedText.contains("# No buttons in the former one-row menu."));
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
