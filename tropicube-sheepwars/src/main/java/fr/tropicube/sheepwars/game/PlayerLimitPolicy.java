package fr.tropicube.sheepwars.game;

/** Pure validation rules shared by SheepWars admission and the host menu. */
public final class PlayerLimitPolicy {
    public static final int MINIMUM = 2;
    public static final int MAXIMUM = 16;

    private PlayerLimitPolicy() { }

    public static int maximum(int configured) {
        return Math.clamp(configured, MINIMUM, MAXIMUM);
    }

    public static int minimum(int configured, int maximum) {
        return Math.min(maximum(maximum), Math.max(MINIMUM, configured));
    }

    public static int decreaseMaximum(int configured, int minimum, int currentPlayers) {
        return Math.max(Math.max(MINIMUM, minimum), Math.max(currentPlayers, maximum(configured) - 1));
    }
}
