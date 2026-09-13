package fr.tropicube.fallenkingdoms.config;

import fr.tropicube.fallenkingdoms.game.PhaseTimeline;
import org.bukkit.configuration.file.FileConfiguration;

/** Validated server-side values that define a Fallen Kingdoms session. */
public record FallenKingdomsSettings(int countdownSeconds, int resultDisplaySeconds, int minPlayersPerKingdom,
                                     int maxPlayersPerKingdom, int maxKingdoms, PhaseTimeline timeline,
                                     double heartHealth, int respawnDelaySeconds, double finalBorderSize) {
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
        return new FallenKingdomsSettings(countdown, display, min, max, kingdoms,
                new PhaseTimeline(config.getInt("phases.pvp-at-seconds"), config.getInt("phases.assault-at-seconds"),
                        config.getInt("phases.sudden-death-at-seconds"), config.getInt("phases.force-end-at-seconds")), heart, respawn, border);
    }
}
