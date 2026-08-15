package fr.tropicube.sheepwars.game;

/** Pure numerical rules used by the SheepWars 1.8-style combat adapter. */
public final class LegacyCombatRules {

    private LegacyCombatRules() {
    }

    public static double meleeDamage(double modernDamage, boolean woodenSword, boolean stoneSword,
                                     double woodenSwordDamage, double stoneSwordDamage) {
        if (modernDamage < 0 || woodenSwordDamage <= 0 || stoneSwordDamage <= 0) {
            throw new IllegalArgumentException("Les dégâts de mêlée doivent être positifs");
        }
        if (woodenSword) return modernDamage * woodenSwordDamage / 4.0;
        if (stoneSword) return modernDamage * stoneSwordDamage / 5.0;
        return modernDamage;
    }

    public static double bowDamage(double damage, double multiplier) {
        if (damage < 0 || multiplier <= 0) throw new IllegalArgumentException("Dégâts d'arc invalides");
        return damage * multiplier;
    }

    public static Knockback knockback(double previousX, double previousY, double previousZ,
                                      double directionX, double directionZ, boolean sprinting,
                                      double horizontal, double sprintHorizontal, double vertical) {
        if (horizontal <= 0 || sprintHorizontal <= 0 || vertical <= 0) {
            throw new IllegalArgumentException("Les paramètres de recul doivent être positifs");
        }
        double length = Math.hypot(directionX, directionZ);
        if (length < 1.0E-6) return new Knockback(previousX, previousY, previousZ);
        double appliedHorizontal = sprinting ? sprintHorizontal : horizontal;
        return new Knockback(previousX / 2.0 + directionX / length * appliedHorizontal,
                Math.min(vertical, previousY / 2.0 + vertical),
                previousZ / 2.0 + directionZ / length * appliedHorizontal);
    }

    public record Knockback(double x, double y, double z) {
    }
}
