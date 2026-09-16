package fr.tropicube.fallenkingdoms.config;

/** Resource drop adjustments applied without replacing unrelated vanilla loot. */
public record DropSettings(double flintBaseChance, double creeperGunpowderMultiplier) {
    public DropSettings {
        if (!Double.isFinite(flintBaseChance) || flintBaseChance < 0 || flintBaseChance > 1)
            throw new IllegalArgumentException("drops.flint-base-chance: probabilité attendue entre 0 et 1");
        if (!Double.isFinite(creeperGunpowderMultiplier) || creeperGunpowderMultiplier < 1
                || creeperGunpowderMultiplier > 10)
            throw new IllegalArgumentException("drops.creeper-gunpowder-multiplier: valeur attendue entre 1 et 10");
    }
}
