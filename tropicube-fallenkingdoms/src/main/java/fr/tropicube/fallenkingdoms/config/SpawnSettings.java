package fr.tropicube.fallenkingdoms.config;

/** Spawn throttling values local to a Fallen Kingdoms session. */
public record SpawnSettings(double naturalHostileNightRetention) {
    public SpawnSettings {
        if (!Double.isFinite(naturalHostileNightRetention)
                || naturalHostileNightRetention < 0 || naturalHostileNightRetention > 1)
            throw new IllegalArgumentException("spawns.natural-hostile-night-retention: probabilité attendue entre 0 et 1");
    }
}
