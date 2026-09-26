package fr.tropicube.fallenkingdoms.game;

import java.util.Locale;

/** Resolves configured kit content to localized presentation keys without exposing technical IDs. */
public final class KitContentPresentation {
    private KitContentPresentation() {
    }

    /** Returns the language key for the configured material and optional potion variant. */
    public static String itemKey(KitDefinition.KitItem item) {
        String material = normalized(item.material().name());
        if (item.potionType() == null) return "fk.kit-item-" + material;
        return "fk.kit-item-" + material + "-" + normalized(item.potionType());
    }

    /** Returns the language key for a validated Minecraft enchantment identifier. */
    public static String enchantmentKey(String enchantment) {
        return "fk.kit-enchantment-" + normalized(enchantment);
    }

    /** Formats ordinary enchantment levels as compact Roman numerals. */
    public static String romanLevel(int level) {
        if (level < 1 || level > 10) return Integer.toString(level);
        StringBuilder result = new StringBuilder();
        int remaining = level;
        int[] values = {10, 9, 5, 4, 1};
        String[] symbols = {"X", "IX", "V", "IV", "I"};
        for (int index = 0; index < values.length; index++) {
            while (remaining >= values[index]) {
                result.append(symbols[index]);
                remaining -= values[index];
            }
        }
        return result.toString();
    }

    private static String normalized(String value) {
        return value.toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
