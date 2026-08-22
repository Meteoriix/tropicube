package fr.tropicube.sheepwars.competitive;

import fr.tropicube.sheepwars.player.PlayerKit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** Validated, versioned catalogue of the two balanced choices for every playable kit. */
public final class KitMasteryCatalog {
    public record Branch(String descriptionKey, Map<String, Double> effects) {
        public Branch { effects = Map.copyOf(effects); }
        public double effect(String key, double fallback) { return effects.getOrDefault(key, fallback); }
    }
    private final int version;
    private final int unlockLevel;
    private final Map<PlayerKit, Map<KitMasteryBranch, Branch>> kits;

    private KitMasteryCatalog(int version, int unlockLevel,
                              Map<PlayerKit, Map<KitMasteryBranch, Branch>> kits) {
        this.version = version;
        this.unlockLevel = unlockLevel;
        this.kits = Map.copyOf(kits);
    }

    public static KitMasteryCatalog load(File file) {
        return parse(YamlConfiguration.loadConfiguration(file));
    }

    static KitMasteryCatalog parse(YamlConfiguration yaml) {
        int version = yaml.getInt("version");
        int unlock = yaml.getInt("unlock-level");
        if (version < 1) throw new IllegalArgumentException("kit-mastery.yml: version doit être positive");
        if (unlock < 1) throw new IllegalArgumentException("kit-mastery.yml: unlock-level doit être positif");
        EnumMap<PlayerKit, Map<KitMasteryBranch, Branch>> definitions = new EnumMap<>(PlayerKit.class);
        for (PlayerKit kit : PlayerKit.values()) {
            if (kit == PlayerKit.NONE) continue;
            EnumMap<KitMasteryBranch, Branch> branches = new EnumMap<>(KitMasteryBranch.class);
            for (KitMasteryBranch branch : KitMasteryBranch.values()) {
                String path = "kits." + kit.name() + "." + branch.name();
                ConfigurationSection section = yaml.getConfigurationSection(path);
                if (section == null) throw new IllegalArgumentException("kit-mastery.yml: branche absente " + path);
                String description = section.getString("description-key");
                if (description == null || description.isBlank()) {
                    throw new IllegalArgumentException("kit-mastery.yml: description-key absente " + path);
                }
                Map<String, Double> effects = new LinkedHashMap<>();
                ConfigurationSection effectSection = section.getConfigurationSection("effects");
                if (effectSection == null || effectSection.getKeys(false).isEmpty()) {
                    throw new IllegalArgumentException("kit-mastery.yml: effects absents " + path);
                }
                for (String key : effectSection.getKeys(false)) {
                    Object raw = effectSection.get(key);
                    if (!(raw instanceof Number number) || !Double.isFinite(number.doubleValue())) {
                        throw new IllegalArgumentException("kit-mastery.yml: effet invalide " + path + ".effects." + key);
                    }
                    effects.put(key, number.doubleValue());
                }
                branches.put(branch, new Branch(description, effects));
            }
            definitions.put(kit, Map.copyOf(branches));
        }
        return new KitMasteryCatalog(version, unlock, definitions);
    }

    public int version() { return version; }
    public int unlockLevel() { return unlockLevel; }
    public Branch branch(PlayerKit kit, KitMasteryBranch branch) { return kits.get(kit).get(branch); }
}
