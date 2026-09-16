package fr.tropicube.fallenkingdoms.game;

/** Pure spawn and loot rules used by the Paper listener. */
public final class WorldBalanceRules {
    private WorldBalanceRules() { }

    public static boolean keepSpawn(boolean active, boolean night, boolean natural, boolean hostile,
                                    double retention, double sample) {
        return !active || !night || !natural || !hostile || sample < retention;
    }

    public static double flintChance(double baseChance, int fortuneLevel) {
        double vanillaChance = switch (Math.max(0, fortuneLevel)) {
            case 0 -> 0.10;
            case 1 -> 1.0 / 7.0;
            case 2 -> 0.25;
            default -> 1.0;
        };
        return Math.min(1.0, vanillaChance * baseChance / 0.10);
    }

    public static int multiplyDrop(int amount, double multiplier) {
        if (amount <= 0) return 0;
        return Math.max(amount, (int) Math.round(amount * multiplier));
    }
}
