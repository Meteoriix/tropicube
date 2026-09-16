package fr.tropicube.sheepwars.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Migrates persisted SheepWars menu layouts whose geometry changed between releases. */
public final class SheepWarsMenuConfigMigration {
    private static final List<Integer> LEGACY_MAP_SLOTS = List.of(0, 1, 2, 3, 4, 5, 6, 7, 8);
    private static final List<String> MAP_MENU_IDS = List.of("map-vote", "map-pick");

    private SheepWarsMenuConfigMigration() { }

    /**
     * Replaces the structural fields of the former one-row map menus with their
     * bundled layout. Other menus and customized scalar values remain untouched.
     *
     * @param file persisted menu manifest
     * @param defaultsStream current bundled menu manifest
     * @return {@code true} when at least one legacy layout was migrated
     */
    public static boolean migrate(File file, InputStream defaultsStream)
            throws IOException, InvalidConfigurationException {
        YamlConfiguration current = new YamlConfiguration();
        current.options().parseComments(true);
        current.load(file);

        YamlConfiguration defaults = new YamlConfiguration();
        defaults.load(new InputStreamReader(defaultsStream, StandardCharsets.UTF_8));
        boolean changed = migrate(current, defaults);
        if (changed) current.save(file);
        return changed;
    }

    static boolean migrate(YamlConfiguration current, YamlConfiguration defaults) {
        boolean changed = false;
        for (String menuId : MAP_MENU_IDS) {
            String path = "menus." + menuId;
            if (!isLegacyMapLayout(current, path)) continue;

            current.set(path + ".rows", defaults.getInt(path + ".rows"));
            current.set(path + ".frame", defaults.getString(path + ".frame"));
            copySection(defaults, current, path + ".buttons");
            current.set(path + ".dynamic-regions.maps.slots",
                    defaults.getIntegerList(path + ".dynamic-regions.maps.slots"));
            changed = true;
        }
        return changed;
    }

    private static boolean isLegacyMapLayout(YamlConfiguration yaml, String path) {
        return yaml.getInt(path + ".rows", -1) == 1
                && "neutral".equals(yaml.getString(path + ".frame"))
                && LEGACY_MAP_SLOTS.equals(yaml.getIntegerList(path + ".dynamic-regions.maps.slots"));
    }

    private static void copySection(YamlConfiguration source, YamlConfiguration target, String path) {
        ConfigurationSection section = source.getConfigurationSection(path);
        if (section == null) throw new IllegalArgumentException("Section de menu embarquée manquante : " + path);
        target.set(path, null);
        for (String key : section.getKeys(true)) {
            if (!section.isConfigurationSection(key)) target.set(path + "." + key, section.get(key));
        }
    }
}
