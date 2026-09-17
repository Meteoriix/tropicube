package fr.tropicube.sheepwars;

import fr.tropicube.sheepwars.competitive.RankTier;
import fr.tropicube.sheepwars.competitive.SheepWarsMode;
import fr.tropicube.sheepwars.player.PlayerClass;
import fr.tropicube.sheepwars.player.PlayerKit;
import fr.tropicube.sheepwars.powerup.TeamPowerUpType;
import fr.tropicube.sheepwars.sheep.SheepType;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SheepWarsLanguageKeysTest {
    private static final Pattern JAVA_KEY = Pattern.compile("\\\"(sw\\.[a-z0-9][a-z0-9.-]*)\\\"");

    @Test
    void everyReferencedKeyExistsInEveryLanguage() throws IOException {
        Set<String> required = referencedLiteralKeys();
        required.addAll(referencedResourceKeys());
        addGeneratedKeys(required);

        for (String language : List.of("fr", "en", "de", "es")) {
            YamlConfiguration translations = YamlConfiguration.loadConfiguration(
                    Path.of("../tropicube-core/src/main/resources/languages", language + ".yml").toFile());
            for (String key : required) {
                assertTrue(translations.isSet(key), () -> key + " manquant dans " + language + ".yml");
            }
        }
    }

    @Test
    void everyScoreboardVariantDisplaysTheSelectedKit() {
        YamlConfiguration resource = YamlConfiguration.loadConfiguration(
                Path.of("src/main/resources/scoreboards.yml").toFile());
        var variants = resource.getConfigurationSection("scoreboards.sheepwars.variants");
        assertTrue(variants != null);

        for (String variant : variants.getKeys(false)) {
            long kitLines = variants.getMapList(variant + ".lines").stream()
                    .filter(line -> "sw.sb-kit".equals(line.get("key")))
                    .count();
            assertEquals(1, kitLines, () -> variant + " doit afficher exactement une ligne de kit");
        }
    }

    @Test
    void everyScoreboardVariantDisplaysABlankLineAfterTheMap() {
        YamlConfiguration resource = YamlConfiguration.loadConfiguration(
                Path.of("src/main/resources/scoreboards.yml").toFile());
        var variants = resource.getConfigurationSection("scoreboards.sheepwars.variants");
        assertTrue(variants != null);

        for (String variant : variants.getKeys(false)) {
            List<Map<?, ?>> lines = variants.getMapList(variant + ".lines");
            int mapLine = indexOfLine(lines, "key", "sw.sb-map");
            int blankLine = indexOfLine(lines, "blank", true);

            assertTrue(mapLine >= 0, () -> variant + " doit afficher la carte");
            assertEquals(mapLine + 1, blankLine,
                    () -> variant + " doit afficher exactement une ligne vide après la carte");
            assertEquals(1, lines.stream().filter(line -> Boolean.TRUE.equals(line.get("blank"))).count(),
                    () -> variant + " doit contenir exactement une ligne vide");
        }
    }

    private static int indexOfLine(List<Map<?, ?>> lines, String property, Object value) {
        for (int index = 0; index < lines.size(); index++) {
            if (value.equals(lines.get(index).get(property))) return index;
        }
        return -1;
    }

    private static Set<String> referencedLiteralKeys() throws IOException {
        Set<String> keys = new LinkedHashSet<>();
        try (var files = Files.walk(Path.of("src/main/java"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                var matcher = JAVA_KEY.matcher(Files.readString(file));
                while (matcher.find()) {
                    String key = matcher.group(1);
                    if (!key.endsWith("-")) keys.add(key);
                }
            }
        }
        return keys;
    }

    private static Set<String> referencedResourceKeys() throws IOException {
        Set<String> keys = new LinkedHashSet<>();
        try (var files = Files.walk(Path.of("src/main/resources"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".yml")).toList()) {
                YamlConfiguration resource = YamlConfiguration.loadConfiguration(file.toFile());
                resource.getValues(true).values().forEach(value -> collectResourceKey(value, keys));
            }
        }
        return keys;
    }

    private static void collectResourceKey(Object value, Set<String> keys) {
        if (value instanceof String key && JAVA_KEY.matcher('"' + key + '"').matches()) {
            keys.add(key);
        } else if (value instanceof Iterable<?> values) {
            values.forEach(element -> collectResourceKey(element, keys));
        }
    }

    private static void addGeneratedKeys(Set<String> keys) {
        for (PlayerClass playerClass : PlayerClass.values()) {
            String suffix = playerClass.name().toLowerCase(Locale.ROOT);
            keys.add("sw.catalog-class-" + suffix + "-name");
            keys.add("sw.catalog-class-" + suffix + "-description");
            keys.add("sw.sb-class-" + suffix);
        }
        for (PlayerKit kit : PlayerKit.values()) {
            String suffix = kit.name().toLowerCase(Locale.ROOT);
            keys.add("sw.catalog-kit-" + suffix + "-name");
            keys.add("sw.catalog-kit-" + suffix + "-description");
        }
        for (SheepType type : SheepType.values()) {
            String suffix = type.name().toLowerCase(Locale.ROOT);
            keys.add("sw.catalog-sheep-" + suffix + "-name");
            keys.add("sw.catalog-sheep-" + suffix + "-description");
        }
        for (RankTier tier : RankTier.values()) {
            keys.add("sw.rank-" + tier.name().toLowerCase(Locale.ROOT));
        }
        for (TeamPowerUpType type : TeamPowerUpType.values()) keys.add(type.languageKey());
        for (SheepWarsMode mode : SheepWarsMode.values()) keys.add(mode.summaryLanguageKey());
        keys.add("sw.mastery-branch-a-name");
        keys.add("sw.mastery-branch-b-name");
    }
}
