package fr.tropicube.core.ui;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable, validated scoreboard layout loaded from a module-owned YAML manifest. */
public record ScoreboardTemplate(String id, String titleKey, Map<String, List<Line>> variants) {

    public ScoreboardTemplate {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(titleKey, "titleKey");
        variants = Map.copyOf(variants);
        if (variants.isEmpty()) throw new IllegalArgumentException("Le scoreboard " + id + " ne possède aucune variante");
    }

    public List<Line> lines(String variant) {
        List<Line> lines = variants.get(variant);
        if (lines == null) throw new IllegalArgumentException("Variante inconnue " + id + ":" + variant);
        return lines;
    }

    /** Loads one scoreboard while preserving the line order declared in YAML. */
    public static ScoreboardTemplate load(JavaPlugin plugin, String resource, String id) {
        File file = new File(plugin.getDataFolder(), resource);
        if (!file.exists()) plugin.saveResource(resource, false);
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        int version = yaml.getInt("version", -1);
        if (version != 1) throw new IllegalArgumentException(resource + ": version attendue=1, reçue=" + version);
        ConfigurationSection root = yaml.getConfigurationSection("scoreboards." + id);
        if (root == null) throw new IllegalArgumentException(resource + ": scoreboard manquant " + id);
        String titleKey = require(root, "title-key", resource);
        ConfigurationSection variants = root.getConfigurationSection("variants");
        if (variants == null) throw new IllegalArgumentException(resource + ": variantes manquantes pour " + id);
        Map<String, List<Line>> parsed = new LinkedHashMap<>();
        for (String variant : variants.getKeys(false)) {
            List<?> source = variants.getList(variant + ".lines");
            if (source == null) throw new IllegalArgumentException(resource + ": lignes manquantes pour " + variant);
            if (source.isEmpty() || source.size() > 15) {
                throw new IllegalArgumentException(resource + ": " + variant + " doit contenir entre 1 et 15 lignes");
            }
            List<Line> lines = new ArrayList<>();
            for (Object entry : source) {
                if (!(entry instanceof Map<?, ?> map)) {
                    throw new IllegalArgumentException(resource + ": ligne invalide dans " + variant);
                }
                boolean blank = Boolean.TRUE.equals(map.get("blank"));
                Object key = map.get("key");
                if (blank == (key != null)) {
                    throw new IllegalArgumentException(resource + ": une ligne doit définir exactement key ou blank");
                }
                lines.add(blank ? Line.empty() : Line.localized(String.valueOf(key)));
            }
            parsed.put(variant, List.copyOf(lines));
        }
        return new ScoreboardTemplate(id, titleKey, parsed);
    }

    private static String require(ConfigurationSection section, String path, String resource) {
        String value = section.getString(path);
        if (value == null || value.isBlank()) throw new IllegalArgumentException(resource + ": clé manquante " + path);
        return value;
    }

    public record Line(String key, boolean blank) {
        public static Line localized(String key) { return new Line(Objects.requireNonNull(key, "key"), false); }
        public static Line empty() { return new Line(null, true); }
    }
}
