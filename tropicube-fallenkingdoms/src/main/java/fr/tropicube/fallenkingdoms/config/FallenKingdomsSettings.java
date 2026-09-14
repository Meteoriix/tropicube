package fr.tropicube.fallenkingdoms.config;

import fr.tropicube.fallenkingdoms.game.PhaseTimeline;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.Material;
import java.util.Set;
import java.util.stream.Collectors;

/** Validated server-side values that define a Fallen Kingdoms session. */
public record FallenKingdomsSettings(int countdownSeconds, int resultDisplaySeconds, int minPlayersPerKingdom,
                                     int maxPlayersPerKingdom, int maxKingdoms, PhaseTimeline timeline,
                                     double heartHealth, int respawnDelaySeconds, double finalBorderSize,
                                     Set<Material> forbiddenBaseMaterials, Set<Material> commonPlacementMaterials,
                                     boolean autoStart, int ruinWaves, int ruinTicksBetweenWaves,
                                     double ruinRadius, double ruinDestructionRatio, boolean preserveContainers) {
    public static FallenKingdomsSettings load(FileConfiguration config) {
        int min = config.getInt("game.min-players-per-kingdom");
        int max = config.getInt("game.max-players-per-kingdom");
        int kingdoms = config.getInt("game.max-kingdoms");
        if (min != 4 || max < min || max > 6 || kingdoms < 2 || kingdoms > 5) throw new IllegalArgumentException("game: capacité de royaume invalide (public: 4 à 6, 2 à 5 royaumes).");
        double heart = config.getDouble("hearts.max-health");
        double border = config.getDouble("sudden-death.final-border-size");
        if (heart <= 0 || border != 50.0) throw new IllegalArgumentException("hearts.max-health doit être positif et sudden-death.final-border-size doit valoir 50.");
        int countdown = config.getInt("game.countdown-seconds"), display = config.getInt("game.result-display-seconds"), respawn = config.getInt("respawn.delay-seconds");
        if (countdown <= 0 || display <= 0 || respawn < 0) throw new IllegalArgumentException("Les délais doivent être valides.");
        Set<Material> forbidden = materials(config, "protections.forbidden-base-materials");
        Set<Material> common = materials(config, "protections.common-placement-whitelist");
        int ruinWaves = config.getInt("ruins.waves");
        int ruinTicks = config.getInt("ruins.ticks-between-waves");
        double ruinRadius = config.getDouble("ruins.radius");
        double ruinRatio = config.getDouble("ruins.destruction-ratio");
        if (ruinWaves < 1 || ruinWaves > 10 || ruinTicks < 1 || ruinRadius <= 0 || ruinRadius > 32
                || ruinRatio <= 0 || ruinRatio > 1)
            throw new IllegalArgumentException("ruins: vagues, délai, rayon ou proportion invalide");
        return new FallenKingdomsSettings(countdown, display, min, max, kingdoms,
                new PhaseTimeline(config.getInt("phases.pvp-at-seconds"), config.getInt("phases.assault-at-seconds"),
                        config.getInt("phases.sudden-death-at-seconds"), config.getInt("phases.force-end-at-seconds")), heart, respawn, border,
                forbidden, common, config.getBoolean("game.auto-start", true), ruinWaves, ruinTicks,
                ruinRadius, ruinRatio, config.getBoolean("ruins.preserve-containers", true));
    }
    private static Set<Material> materials(FileConfiguration config, String path) {
        try {
            return config.getStringList(path).stream().map(value -> Material.valueOf(value.toUpperCase(java.util.Locale.ROOT)))
                    .collect(Collectors.toUnmodifiableSet());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(path + ": matériau inconnu", exception);
        }
    }
}
