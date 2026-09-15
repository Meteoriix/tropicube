package fr.tropicube.fallenkingdoms.game;

import org.bukkit.Material;

/** Pure calculations used by the limited 1.8 combat emulation. */
public final class LegacyCombatRules {
    private LegacyCombatRules() { }

    public static double attackDamage(double vanillaDamage) {
        if (vanillaDamage < 0) throw new IllegalArgumentException("Les dégâts doivent être positifs");
        return vanillaDamage;
    }

    public static double attackDamage(Material weapon, double vanillaDamage) {
        if (vanillaDamage < 0) throw new IllegalArgumentException("Les dégâts doivent être positifs");
        String name = weapon.name();
        double modernBase = modernBase(name);
        double legacyBase = legacyBase(name);
        return Math.max(0, legacyBase + Math.max(0, vanillaDamage - modernBase));
    }

    private static double modernBase(String name) {
        if (name.endsWith("_SWORD")) return switch (name) {
            case "WOODEN_SWORD", "GOLDEN_SWORD" -> 4; case "STONE_SWORD" -> 5; case "IRON_SWORD" -> 6;
            case "DIAMOND_SWORD" -> 7; default -> 8;
        };
        if (name.endsWith("_AXE")) return switch (name) {
            case "WOODEN_AXE", "GOLDEN_AXE" -> 7; case "STONE_AXE", "IRON_AXE", "DIAMOND_AXE" -> 9; default -> 10;
        };
        if (name.endsWith("_PICKAXE")) return switch (name) {
            case "WOODEN_PICKAXE", "GOLDEN_PICKAXE" -> 2; case "STONE_PICKAXE" -> 3; case "IRON_PICKAXE" -> 4;
            case "DIAMOND_PICKAXE" -> 5; default -> 6;
        };
        return 1;
    }

    private static double legacyBase(String name) {
        if (name.endsWith("_SWORD")) return switch (name) {
            case "WOODEN_SWORD", "GOLDEN_SWORD" -> 5; case "STONE_SWORD" -> 6; case "IRON_SWORD" -> 7;
            case "DIAMOND_SWORD" -> 8; default -> 9;
        };
        if (name.endsWith("_AXE")) return switch (name) {
            case "WOODEN_AXE", "GOLDEN_AXE" -> 3; case "STONE_AXE" -> 4; case "IRON_AXE" -> 5;
            case "DIAMOND_AXE" -> 6; default -> 7;
        };
        if (name.endsWith("_PICKAXE")) return modernBase(name);
        return 1;
    }

    public static Knockback knockback(double previousX, double previousY, double previousZ,
                                      double directionX, double directionZ, boolean sprinting) {
        double length = Math.hypot(directionX, directionZ);
        if (length < 1.0E-6) return new Knockback(previousX, previousY, previousZ);
        double horizontal = sprinting ? 0.5 : 0.4;
        return new Knockback(previousX / 2.0 + directionX / length * horizontal,
                Math.min(0.4, previousY / 2.0 + 0.4),
                previousZ / 2.0 + directionZ / length * horizontal);
    }

    public record Knockback(double x, double y, double z) { }
}
