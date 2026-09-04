package fr.tropicube.sheepwars.powerup;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.EnumMap;
import java.util.Map;

/** Immutable, validated configuration of SheepWars team power-ups. */
public record TeamPowerUpSettings(
        boolean enabled,
        int respawnTicks,
        double hitRadius,
        Map<TeamPowerUpType, Integer> weights,
        double healingHealth,
        int poisonArrowCount,
        int poisonDurationTicks,
        int poisonAmplifier,
        int speedDurationTicks,
        int speedAmplifier
) {
    private static final String ROOT = "team-powerups";

    public TeamPowerUpSettings {
        weights = Map.copyOf(weights);
    }

    /** Loads the complete section and rejects unsafe or unusable gameplay values. */
    public static TeamPowerUpSettings load(FileConfiguration configuration) {
        ConfigurationSection section = requireSection(configuration, ROOT);
        boolean enabled = section.getBoolean("enabled", true);
        int respawnSeconds = boundedPositiveInt(section, "respawn-seconds", 3600);
        double hitRadius = boundedPositiveDouble(section, "hit-radius", 3.0);

        ConfigurationSection effects = requireSection(section, "effects");
        EnumMap<TeamPowerUpType, Integer> weights = new EnumMap<>(TeamPowerUpType.class);
        for (TeamPowerUpType type : TeamPowerUpType.values()) {
            ConfigurationSection effect = requireSection(effects, type.configKey());
            weights.put(type, nonNegativeInt(effect, "weight"));
        }
        if (weights.values().stream().mapToLong(Integer::longValue).sum() <= 0) {
            throw invalid("effects.*.weight", "au moins un poids doit être strictement positif");
        }

        ConfigurationSection healing = requireSection(effects, TeamPowerUpType.HEALING.configKey());
        ConfigurationSection poison = requireSection(effects, TeamPowerUpType.POISON_ARROWS.configKey());
        ConfigurationSection speed = requireSection(effects, TeamPowerUpType.SPEED.configKey());
        return new TeamPowerUpSettings(enabled, respawnSeconds * 20, hitRadius, weights,
                boundedPositiveDouble(healing, "health", 40.0), boundedPositiveInt(poison, "arrow-count", 64),
                boundedPositiveInt(poison, "duration-seconds", 600) * 20,
                boundedNonNegativeInt(poison, "amplifier", 4),
                boundedPositiveInt(speed, "duration-seconds", 600) * 20,
                boundedNonNegativeInt(speed, "amplifier", 4));
    }

    private static ConfigurationSection requireSection(ConfigurationSection parent, String path) {
        ConfigurationSection section = parent.getConfigurationSection(path);
        if (section == null) throw invalid(path, "section manquante");
        return section;
    }

    private static int boundedPositiveInt(ConfigurationSection section, String path, int maximum) {
        int value = integer(section, path);
        if (value <= 0 || value > maximum) {
            throw invalid(path, "entier attendu entre 1 et " + maximum);
        }
        return value;
    }

    private static int nonNegativeInt(ConfigurationSection section, String path) {
        int value = integer(section, path);
        if (value < 0) throw invalid(path, "entier positif ou nul attendu");
        return value;
    }

    private static int boundedNonNegativeInt(ConfigurationSection section, String path, int maximum) {
        int value = nonNegativeInt(section, path);
        if (value > maximum) throw invalid(path, "entier attendu entre 0 et " + maximum);
        return value;
    }

    private static int integer(ConfigurationSection section, String path) {
        Object value = section.get(path);
        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())
                || number.doubleValue() < Integer.MIN_VALUE || number.doubleValue() > Integer.MAX_VALUE
                || number.doubleValue() != Math.rint(number.doubleValue())) {
            throw invalid(path, "entier attendu");
        }
        return number.intValue();
    }

    private static double boundedPositiveDouble(ConfigurationSection section, String path, double maximum) {
        Object value = section.get(path);
        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())
                || number.doubleValue() <= 0 || number.doubleValue() > maximum) {
            throw invalid(path, "nombre attendu entre 0 (exclu) et " + maximum);
        }
        return number.doubleValue();
    }

    private static IllegalArgumentException invalid(String path, String reason) {
        return new IllegalArgumentException("Configuration " + ROOT + "." + path + " invalide : " + reason);
    }
}
