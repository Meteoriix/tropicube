package fr.tropicube.core.ui;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Immutable, validated tablist layout loaded from a module-owned YAML manifest. */
public record TablistTemplate(String id, Map<String, Variant> variants) {

    public TablistTemplate {
        Objects.requireNonNull(id, "id");
        variants = Map.copyOf(variants);
        if (variants.isEmpty()) throw new IllegalArgumentException("La tablist " + id + " ne possède aucune variante");
    }

    /** Returns a validated variant or fails with the owning tablist in the error. */
    public Variant variant(String name) {
        Variant variant = variants.get(name);
        if (variant == null) throw new IllegalArgumentException("Variante inconnue " + id + ":" + name);
        return variant;
    }

    /** Loads one tablist while preserving the variant order declared in YAML. */
    public static TablistTemplate load(JavaPlugin plugin, String resource, String id) {
        File file = new File(plugin.getDataFolder(), resource);
        if (!file.exists()) plugin.saveResource(resource, false);
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        return parse(yaml, resource, id);
    }

    static TablistTemplate parse(YamlConfiguration yaml, String resource, String id) {
        int version = yaml.getInt("version", -1);
        if (version != 1) throw new IllegalArgumentException(resource + ": version attendue=1, reçue=" + version);
        ConfigurationSection root = yaml.getConfigurationSection("tablists." + id);
        if (root == null) throw new IllegalArgumentException(resource + ": tablist manquante " + id);
        ConfigurationSection variants = root.getConfigurationSection("variants");
        if (variants == null || variants.getKeys(false).isEmpty()) {
            throw new IllegalArgumentException(resource + ": variantes manquantes pour " + id);
        }
        Map<String, Variant> parsed = new LinkedHashMap<>();
        for (String name : variants.getKeys(false)) {
            ConfigurationSection source = variants.getConfigurationSection(name);
            if (source == null) throw new IllegalArgumentException(resource + ": variante invalide " + name);
            parsed.put(name, new Variant(require(source, "header-key", resource),
                    require(source, "footer-key", resource)));
        }
        return new TablistTemplate(id, parsed);
    }

    private static String require(ConfigurationSection section, String path, String resource) {
        String value = section.getString(path);
        if (value == null || value.isBlank()) throw new IllegalArgumentException(resource + ": clé manquante " + path);
        return value;
    }

    /** Translation keys used for one contextual header/footer pair. */
    public record Variant(String headerKey, String footerKey) {
        public Variant {
            Objects.requireNonNull(headerKey, "headerKey");
            Objects.requireNonNull(footerKey, "footerKey");
        }
    }
}
