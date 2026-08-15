package fr.tropicube.sheepwars.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;

/** Validated access to SheepWars gameplay tuning values. */
public final class GameplayBalance {

    private static final String ROOT = "gameplay-balance";
    private final ConfigurationSection values;

    private GameplayBalance(ConfigurationSection values) {
        this.values = values;
    }

    /** Loads the balance section and rejects missing, non-numeric or negative values. */
    public static GameplayBalance load(FileConfiguration configuration) {
        ConfigurationSection section = configuration.getConfigurationSection(ROOT);
        if (section == null) throw new IllegalArgumentException("Section manquante : " + ROOT);

        List<String> invalid = new ArrayList<>();
        validateNumbers(section, ROOT, invalid);
        if (!invalid.isEmpty()) {
            throw new IllegalArgumentException("Valeurs d'équilibrage invalides : " + String.join(", ", invalid));
        }
        return new GameplayBalance(section);
    }

    private static void validateNumbers(ConfigurationSection section, String prefix, List<String> invalid) {
        for (String key : section.getKeys(false)) {
            Object value = section.get(key);
            String path = prefix + "." + key;
            if (value instanceof ConfigurationSection child) {
                validateNumbers(child, path, invalid);
            } else if (!(value instanceof Number number) || number.doubleValue() < 0
                    || (requiresPositiveValue(key) && number.doubleValue() == 0)) {
                invalid.add(path);
            }
        }
    }

    private static boolean requiresPositiveValue(String key) {
        return key.contains("radius") || key.contains("seconds") || key.contains("period")
                || key.contains("health") || key.contains("multiplier") || key.contains("speed")
                || key.equals("fragments") || key.equals("targets") || key.equals("blocks-per-wave")
                || key.equals("projectile-count") || key.equals("projectile-spread")
                || key.equals("max-stored-sheep");
    }

    public double decimal(String path) {
        require(path);
        return values.getDouble(path);
    }

    public int integer(String path) {
        require(path);
        return values.getInt(path);
    }

    public int ticks(String secondsPath) {
        return (int) Math.round(decimal(secondsPath) * 20.0);
    }

    private void require(String path) {
        if (!values.isSet(path) || !(values.get(path) instanceof Number)) {
            throw new IllegalArgumentException("Valeur d'équilibrage numérique manquante : " + ROOT + "." + path);
        }
    }
}
