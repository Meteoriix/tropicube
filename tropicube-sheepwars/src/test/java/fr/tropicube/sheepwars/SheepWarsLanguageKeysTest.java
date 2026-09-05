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
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

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
