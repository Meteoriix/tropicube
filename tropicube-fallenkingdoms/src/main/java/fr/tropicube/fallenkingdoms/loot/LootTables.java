package fr.tropicube.fallenkingdoms.loot;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Loads and validates the complete day-two-to-day-six loot progression. */
public final class LootTables {
    private final Map<Integer, DailyLootTable> tables;

    private LootTables(Map<Integer, DailyLootTable> tables) {
        this.tables = Map.copyOf(tables);
    }

    public static LootTables load(FileConfiguration config) {
        ConfigurationSection root = required(config, "progressive-loot.tables");
        int rolls = config.getInt("progressive-loot.rolls-per-chest");
        Map<Integer, DailyLootTable> result = new LinkedHashMap<>();
        for (int day = 2; day <= 6; day++) {
            int tableDay = day;
            List<Map<?, ?>> values = root.getMapList(Integer.toString(day));
            if (values.isEmpty()) throw new IllegalArgumentException("progressive-loot.tables." + day + ": table vide");
            List<WeightedLootEntry> entries = values.stream().map(value -> entry(tableDay, value)).toList();
            result.put(day, new DailyLootTable(day, rolls, entries));
        }
        return new LootTables(result);
    }

    public DailyLootTable forDay(int day) {
        DailyLootTable table = tables.get(day);
        if (table == null) throw new IllegalArgumentException("Aucune table de butin pour le jour " + day);
        return table;
    }

    private static WeightedLootEntry entry(int day, Map<?, ?> value) {
        try {
            Material material = Material.valueOf(String.valueOf(value.get("material")).toUpperCase(Locale.ROOT));
            int minimum = number(value, "min");
            int maximum = number(value, "max");
            int weight = number(value, "weight");
            return new WeightedLootEntry(material, minimum, maximum, weight);
        } catch (RuntimeException failure) {
            throw new IllegalArgumentException("progressive-loot.tables." + day + ": entrée invalide", failure);
        }
    }

    private static int number(Map<?, ?> value, String key) {
        Object raw = value.get(key);
        if (!(raw instanceof Number number)) throw new IllegalArgumentException(key + ": entier attendu");
        return number.intValue();
    }

    private static ConfigurationSection required(FileConfiguration config, String path) {
        ConfigurationSection section = config.getConfigurationSection(path);
        if (section == null) throw new IllegalArgumentException(path + ": section manquante");
        return section;
    }
}
