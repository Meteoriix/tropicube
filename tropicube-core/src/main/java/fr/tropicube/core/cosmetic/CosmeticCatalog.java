package fr.tropicube.core.cosmetic;

import org.bukkit.configuration.file.YamlConfiguration;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.HashSet;

/** Immutable cosmetic definitions. Renderer identifiers are validated by Lobby before use. */
public record CosmeticCatalog(List<Entry> entries) {
    /** Independent equipment slots; neither category affects gameplay. */
    public enum Category { TRAIL, SOUND }
    /** Exactly one acquisition rule applies to each definition. */
    public enum Access { FREE, LEVEL, CURRENCY, VIP }
    /** A stable identifier survives catalogue edits and is never displayed to players. */
    public record Entry(String id, Category category, Access access, int requirement, String effect) {
        public Entry {
            if (id == null || !id.matches("[a-z][a-z0-9-]{0,47}") || category == null || access == null
                    || effect == null || effect.isBlank() || requirement < 0
                    || (access == Access.FREE ? requirement != 0 : requirement == 0)
                    || (access == Access.VIP && requirement > 3))
                throw new IllegalArgumentException("cosmetics.entries." + id + ": invalid category/access/requirement/effect");
        }
        public boolean available(int level, int vipLevel, boolean purchased) {
            return switch (access) {
                case FREE -> true;
                case LEVEL -> level >= requirement;
                case VIP -> vipLevel >= requirement;
                case CURRENCY -> purchased;
            };
        }
        public String nameKey() { return "cosmetics.names." + id; }
    }
    public CosmeticCatalog {
        entries = List.copyOf(entries);
        var ids = new HashSet<String>();
        if (entries.isEmpty() || entries.size() > 100) throw new IllegalArgumentException("cosmetics.entries: expected 1..100 entries");
        for (Entry entry : entries) if (!ids.add(entry.id())) throw new IllegalArgumentException("Duplicate cosmetic: " + entry.id());
    }
    /** Ordered future level rewards; already unlocked items are excluded. */
    public List<Entry> upcoming(int level) {
        return entries.stream().filter(entry -> entry.access() == Access.LEVEL && entry.requirement() > level)
                .sorted(java.util.Comparator.comparingInt(Entry::requirement)).toList();
    }
    public Entry find(String id) {
        return entries.stream().filter(entry -> entry.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown cosmetic: " + id));
    }
    /** Strictly parses the catalogue; malformed YAML or coerced scalar values fail startup. */
    public static CosmeticCatalog load(InputStream input) {
        if (input == null) throw new IllegalArgumentException("cosmetics.yml absent");
        var yaml = new YamlConfiguration();
        try {
            yaml.load(new InputStreamReader(input, StandardCharsets.UTF_8));
        } catch (java.io.IOException | org.bukkit.configuration.InvalidConfigurationException error) {
            throw new IllegalArgumentException("cosmetics.yml: invalid YAML", error);
        }
        if (!(yaml.get("version") instanceof Integer version) || version != 1)
            throw invalid("version", yaml.get("version"), "integer 1");
        var root = yaml.getConfigurationSection("entries");
        if (root == null) throw invalid("entries", yaml.get("entries"), "section with 1..100 entries");
        return new CosmeticCatalog(root.getKeys(false).stream().map(id -> {
            String path = "entries." + id + ".";
            Category category = enumValue(Category.class, yaml.get(path + "category"), path + "category");
            Access access = enumValue(Access.class, yaml.get(path + "access"), path + "access");
            Object value = yaml.get(path + "requirement");
            if (!(value instanceof Integer requirement)) throw invalid(path + "requirement", value, "integer >= 0");
            if (!(yaml.get(path + "effect") instanceof String effect) || effect.isBlank())
                throw invalid(path + "effect", yaml.get(path + "effect"), "nonempty renderer identifier");
            return new Entry(id, category, access, requirement, effect);
        }).toList());
    }

    private static <T extends Enum<T>> T enumValue(Class<T> type, Object value, String path) {
        if (value instanceof String text) {
            try { return Enum.valueOf(type, text); } catch (IllegalArgumentException ignored) { }
        }
        throw invalid(path, value, java.util.Arrays.toString(type.getEnumConstants()));
    }

    private static IllegalArgumentException invalid(String path, Object value, String expected) {
        return new IllegalArgumentException("cosmetics." + path + "=" + value + "; expected " + expected);
    }
}
