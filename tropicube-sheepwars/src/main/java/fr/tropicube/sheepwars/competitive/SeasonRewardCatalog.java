package fr.tropicube.sheepwars.competitive;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.EnumMap;
import java.util.Map;

/** Versioned end-of-season comfort rewards, without gameplay advantages. */
public final class SeasonRewardCatalog {
    public record Reward(double currency, boolean title, boolean badge) { }
    private final int version;
    private final Map<RankTier, Reward> rewards;

    private SeasonRewardCatalog(int version, Map<RankTier, Reward> rewards) {
        this.version = version; this.rewards = Map.copyOf(rewards);
    }

    public static SeasonRewardCatalog load(File file) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        int version = yaml.getInt("version");
        if (version < 1) throw new IllegalArgumentException("season-rewards.yml: version doit être positive");
        EnumMap<RankTier, Reward> values = new EnumMap<>(RankTier.class);
        for (RankTier tier : RankTier.values()) {
            String path = "tiers." + tier.name();
            if (!yaml.isConfigurationSection(path)) throw new IllegalArgumentException(
                    "season-rewards.yml: rang absent " + tier);
            double currency = yaml.getDouble(path + ".currency", -1);
            if (currency < 0 || !Double.isFinite(currency)) throw new IllegalArgumentException(
                    "season-rewards.yml: monnaie invalide " + tier);
            values.put(tier, new Reward(currency, yaml.getBoolean(path + ".title", true),
                    yaml.getBoolean(path + ".badge", true)));
        }
        return new SeasonRewardCatalog(version, values);
    }

    public int version() { return version; }
    public Reward reward(RankTier tier) { return rewards.get(tier); }
}
