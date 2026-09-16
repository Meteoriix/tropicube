package fr.tropicube.fallenkingdoms.config;

import fr.tropicube.fallenkingdoms.game.PhaseTimeline;
import fr.tropicube.fallenkingdoms.game.CombatProfile;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.Material;
import java.util.Set;
import java.util.stream.Collectors;

/** Validated server-side values that define a Fallen Kingdoms session. */
public record FallenKingdomsSettings(int countdownSeconds, int resultDisplaySeconds, int minPlayersPerKingdom,
                                     int maxPlayersPerKingdom, int maxKingdoms, PhaseTimeline timeline,
                                     double heartHealth, int respawnDelaySeconds, double finalBorderSize,
                                     Set<Material> forbiddenPlacementMaterials,
                                      boolean autoStart, int ruinWaves, int ruinTicksBetweenWaves,
                                      double ruinRadius, double ruinDestructionRatio, boolean preserveContainers,
                                      CombatProfile combatProfile, boolean tntBreachesEnabled,
                                      boolean dropInventory, boolean disconnectCountsAsDeath) {
    public static FallenKingdomsSettings load(FileConfiguration config) {
        int min = config.getInt("game.min-players-per-kingdom");
        int max = config.getInt("game.max-players-per-kingdom");
        int kingdoms = config.getInt("game.max-kingdoms");
        if (min != 3 || max < min || max > 6 || kingdoms < 2 || kingdoms > 5) throw new IllegalArgumentException("game: capacité de royaume invalide (Quick Play: 3 à 6, 2 à 5 royaumes).");
        double heart = config.getDouble("hearts.max-health");
        double border = config.getDouble("sudden-death.final-border-size");
        if (heart <= 0 || border != 50.0) throw new IllegalArgumentException("hearts.max-health doit être positif et sudden-death.final-border-size doit valoir 50.");
        int countdown = config.getInt("game.countdown-seconds"), display = config.getInt("game.result-display-seconds"), respawn = config.getInt("respawn.delay-seconds");
        if (countdown <= 0 || display <= 0 || respawn < 0) throw new IllegalArgumentException("Les délais doivent être valides.");
        Set<Material> forbidden = config.contains("protections.forbidden-placement-materials")
                ? materials(config, "protections.forbidden-placement-materials")
                : Set.of(Material.BEDROCK, Material.BARRIER, Material.END_PORTAL_FRAME);
        int ruinWaves = config.getInt("ruins.waves");
        int ruinTicks = config.getInt("ruins.ticks-between-waves");
        double ruinRadius = config.getDouble("ruins.radius");
        double ruinRatio = config.getDouble("ruins.destruction-ratio");
        if (ruinWaves < 1 || ruinWaves > 10 || ruinTicks < 1 || ruinRadius <= 0 || ruinRadius > 32
                || ruinRatio <= 0 || ruinRatio > 1)
            throw new IllegalArgumentException("ruins: vagues, délai, rayon ou proportion invalide");
        if(config.getBoolean("combat.friendly-fire",true)||config.getBoolean("hearts.tnt-direct-damage",true)
                ||!config.getBoolean("ruins.preserve-containers",false)||!config.getBoolean("protections.block-portal-bypass",false)
                ||!config.getBoolean("protections.block-teleport-bypass",false)||!config.getBoolean("protections.block-piston-crossing",false)
                ||!config.getBoolean("protections.block-fluid-crossing",false))
            throw new IllegalArgumentException("Les protections techniques obligatoires ne peuvent pas être désactivées");
        CombatProfile profile;
        try { profile = CombatProfile.valueOf(environment("FK_COMBAT_PROFILE", config.getString("combat.default-profile", "LEGACY_1_8"))); }
        catch (IllegalArgumentException failure) { throw new IllegalArgumentException("combat.default-profile: profil inconnu", failure); }
        if (!config.getStringList("combat.allowed-custom-profiles").contains(profile.name()))
            throw new IllegalArgumentException("combat.default-profile: profil non autorisé " + profile);
        boolean host=Boolean.parseBoolean(System.getenv().getOrDefault("IS_HOST","false"));
        int effectiveMin=integerOverride("FK_MIN_PLAYERS_PER_KINGDOM", defaultMinimumPlayersPerKingdom(min, host));
        int effectiveMax=integerOverride("FK_MAX_PLAYERS_PER_KINGDOM",max),effectiveKingdoms=integerOverride("FK_MAX_KINGDOMS",kingdoms);
        if(effectiveMin<1||effectiveMax<effectiveMin||effectiveMax>6||effectiveKingdoms<2||effectiveKingdoms>5)throw new IllegalArgumentException("Surcharges de capacité FK invalides");
        int effectiveCountdown=integerOverride("FK_COUNTDOWN_SECONDS", countdown);
        int effectiveRespawn=integerOverride("FK_RESPAWN_DELAY_SECONDS", respawn);
        double effectiveHeart=doubleOverride("FK_HEART_HEALTH", heart);
        int effectiveRuinWaves=integerOverride("FK_RUIN_WAVES",ruinWaves);
        double effectiveRuinRadius=doubleOverride("FK_RUIN_RADIUS",ruinRadius);
        double effectiveRuinRatio=doubleOverride("FK_RUIN_DESTRUCTION_RATIO",ruinRatio);
        if(effectiveCountdown<=0||effectiveRespawn<0||effectiveHeart<=0||effectiveRuinWaves<1||effectiveRuinWaves>10
                ||effectiveRuinRadius<=0||effectiveRuinRadius>32||effectiveRuinRatio<=0||effectiveRuinRatio>1)
            throw new IllegalArgumentException("Surcharges de délais, cœur ou ruine FK invalides");
        return new FallenKingdomsSettings(effectiveCountdown, display, effectiveMin,
                effectiveMax, effectiveKingdoms,
                new PhaseTimeline(integerOverride("FK_PVP_AT_SECONDS",config.getInt("phases.pvp-at-seconds")),integerOverride("FK_ASSAULT_AT_SECONDS",config.getInt("phases.assault-at-seconds")),
                        integerOverride("FK_SUDDEN_DEATH_AT_SECONDS",config.getInt("phases.sudden-death-at-seconds")),integerOverride("FK_FORCE_END_AT_SECONDS",config.getInt("phases.force-end-at-seconds"))),
                effectiveHeart, effectiveRespawn, border,
                forbidden, booleanOverride("FK_AUTO_START",host?false:config.getBoolean("game.auto-start", true)), effectiveRuinWaves, ruinTicks,
                effectiveRuinRadius, effectiveRuinRatio, config.getBoolean("ruins.preserve-containers", true), profile,
                config.getBoolean("protections.tnt-breaches-enabled", true),
                config.getBoolean("respawn.drop-inventory", true), config.getBoolean("respawn.disconnect-counts-as-death", true));
    }
    static int defaultMinimumPlayersPerKingdom(int configuredMinimum, boolean host) {
        return host ? 4 : configuredMinimum;
    }
    private static String environment(String name, String fallback) {
        String value = System.getenv(name); return value == null || value.isBlank() ? fallback : value.trim().toUpperCase(java.util.Locale.ROOT);
    }
    private static int integerOverride(String name, int fallback) {
        String value = System.getenv(name); if (value == null || value.isBlank()) return fallback;
        try { return Integer.parseInt(value); } catch (NumberFormatException failure) { throw new IllegalArgumentException(name + ": entier attendu", failure); }
    }
    private static double doubleOverride(String name, double fallback) {
        String value = System.getenv(name); if (value == null || value.isBlank()) return fallback;
        try { return Double.parseDouble(value); } catch (NumberFormatException failure) { throw new IllegalArgumentException(name + ": nombre attendu", failure); }
    }
    private static boolean booleanOverride(String name, boolean fallback) {
        String value=System.getenv(name);if(value==null||value.isBlank())return fallback;
        if(!value.equalsIgnoreCase("true")&&!value.equalsIgnoreCase("false"))throw new IllegalArgumentException(name+": booléen attendu");
        return Boolean.parseBoolean(value);
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
