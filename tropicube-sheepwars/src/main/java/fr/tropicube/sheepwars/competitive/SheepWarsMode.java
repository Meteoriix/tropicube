package fr.tropicube.sheepwars.competitive;

import fr.tropicube.docker.model.InstanceMode;

import java.util.Locale;

/** Matchmaking modes supported by a SheepWars instance. */
public enum SheepWarsMode {
    QUICK_PLAY(16, 2, true, false),
    RANKED_4V4(8, 8, false, true),
    RANKED_8V8(16, 16, false, true),
    CUSTOM(16, 2, false, false);

    private final int maximumPlayers;
    private final int minimumPlayers;
    private final boolean kitMasteryEnabled;
    private final boolean ranked;

    SheepWarsMode(int maximumPlayers, int minimumPlayers, boolean kitMasteryEnabled, boolean ranked) {
        this.maximumPlayers = maximumPlayers;
        this.minimumPlayers = minimumPlayers;
        this.kitMasteryEnabled = kitMasteryEnabled;
        this.ranked = ranked;
    }

    public int maximumPlayers() { return maximumPlayers; }
    public int minimumPlayers() { return minimumPlayers; }
    public boolean kitMasteryEnabled() { return kitMasteryEnabled; }
    public boolean ranked() { return ranked; }
    public int teamSize() { return maximumPlayers / 2; }

    /** Resolves the orchestration value while preserving legacy quick-play instances. */
    public static SheepWarsMode fromEnvironment(String value, boolean customGame) {
        if (customGame) return CUSTOM;
        if (value == null || value.isBlank()) return QUICK_PLAY;
        try {
            InstanceMode mode = InstanceMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
            return switch (mode) {
                case RANKED_4V4 -> RANKED_4V4;
                case RANKED_8V8 -> RANKED_8V8;
                case CUSTOM -> CUSTOM;
                default -> QUICK_PLAY;
            };
        } catch (IllegalArgumentException ignored) {
            return QUICK_PLAY;
        }
    }
}
