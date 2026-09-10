package fr.tropicube.lobby.cosmetic;

import org.bukkit.configuration.ConfigurationSection;

/** Validated bounds keep emission frequency, density and spatial search predictable. */
public record CosmeticRenderSettings(int intervalTicks, int particles, int previewTicks, int rangeBlocks) {
    public CosmeticRenderSettings {
        check("render-interval-ticks", intervalTicks, 1, 100);
        check("particles-per-emission", particles, 1, 20);
        check("preview-ticks", previewTicks, 20, 300);
        check("range-blocks", rangeBlocks, 1, 24);
    }

    /** Missing options retain safe defaults; fractional and textual integers are rejected. */
    public static CosmeticRenderSettings load(ConfigurationSection config) {
        return new CosmeticRenderSettings(integer(config, "render-interval-ticks", 5, 1, 100),
                integer(config, "particles-per-emission", 2, 1, 20),
                integer(config, "preview-seconds", 5, 1, 15) * 20,
                integer(config, "range-blocks", 24, 1, 24));
    }

    private static int integer(ConfigurationSection config, String key, int fallback, int minimum, int maximum) {
        Object value = config.get("cosmetics." + key, fallback);
        if (!(value instanceof Integer number)) throw new IllegalArgumentException("cosmetics." + key + "=" + value + "; expected integer " + minimum + ".." + maximum);
        check(key, number, minimum, maximum);
        return number;
    }

    private static void check(String key, int value, int minimum, int maximum) {
        if (value < minimum || value > maximum) throw new IllegalArgumentException("cosmetics." + key + "=" + value + "; expected " + minimum + ".." + maximum);
    }
}
