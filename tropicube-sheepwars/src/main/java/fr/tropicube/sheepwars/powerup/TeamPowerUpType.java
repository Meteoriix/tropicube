package fr.tropicube.sheepwars.powerup;

import org.bukkit.Material;

/** Team-wide bonuses represented by the floating wool targets. */
public enum TeamPowerUpType {
    HEALING("healing", Material.LIME_WOOL, "sw.powerup-healing"),
    POISON_ARROWS("poison-arrows", Material.PURPLE_WOOL, "sw.powerup-poison-arrows"),
    SPEED("speed", Material.LIGHT_BLUE_WOOL, "sw.powerup-speed");

    private final String configKey;
    private final Material material;
    private final String languageKey;

    TeamPowerUpType(String configKey, Material material, String languageKey) {
        this.configKey = configKey;
        this.material = material;
        this.languageKey = languageKey;
    }

    public String configKey() {
        return configKey;
    }

    public Material material() {
        return material;
    }

    public String languageKey() {
        return languageKey;
    }
}
