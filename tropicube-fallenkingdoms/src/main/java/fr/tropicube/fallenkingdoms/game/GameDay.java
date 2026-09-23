package fr.tropicube.fallenkingdoms.game;

/** Converts elapsed game time to the fixed ten-minute Fallen Kingdoms day. */
public final class GameDay {
    public static final int LENGTH_SECONDS = 600;
    public static final int LAST_DAY = 6;

    private GameDay() { }

    public static int at(int elapsedSeconds) {
        return Math.min(LAST_DAY, Math.max(0, elapsedSeconds) / LENGTH_SECONDS + 1);
    }
}
