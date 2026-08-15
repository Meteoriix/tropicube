package fr.tropicube.sheepwars.sheep;

/** Pure calculation used by sheep explosions with linear distance falloff. */
public final class RadialDamage {

    private RadialDamage() {
    }

    /**
     * Computes one hit while respecting an optional per-ability damage budget.
     *
     * @param distance distance from the explosion centre
     * @param radius effect radius
     * @param maximumDamage damage at the centre
     * @param multiplier kit multiplier applied only to damage
     * @param damageAlreadyApplied damage already dealt by the same ability
     * @param damageCap maximum damage for the whole ability, or zero for no cap
     * @return damage to apply for this hit
     */
    public static double calculate(double distance, double radius, double maximumDamage,
                                   double multiplier, double damageAlreadyApplied, double damageCap) {
        if (distance >= radius || radius <= 0 || maximumDamage <= 0 || multiplier <= 0) return 0;
        double damage = maximumDamage * (1.0 - Math.max(0, distance) / radius) * multiplier;
        if (damageCap > 0) {
            double remaining = damageCap * multiplier - Math.max(0, damageAlreadyApplied);
            damage = Math.min(damage, Math.max(0, remaining));
        }
        return Math.max(0, damage);
    }
}
