package fr.tropicube.sheepwars.game;

/** Fixed ranked reconnection windows approved by the game design. */
public final class ReconnectPolicy {
    public static final int PLAYER_GRACE_SECONDS = 180;
    public static final int EMPTY_TEAM_FORFEIT_SECONDS = 30;

    private ReconnectPolicy() { }

    public static boolean expired(long disconnectedAt, long now) {
        return now - disconnectedAt >= PLAYER_GRACE_SECONDS * 1_000L;
    }
}
