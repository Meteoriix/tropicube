package fr.tropicube.fallenkingdoms.game;

/** Converts elapsed session seconds to the target phase exactly once. */
public record PhaseTimeline(int pvpAt, int assaultAt, int suddenDeathAt, int forceEndAt) {
    public PhaseTimeline {
        if (pvpAt <= 0 || pvpAt >= assaultAt || assaultAt >= suddenDeathAt || suddenDeathAt >= forceEndAt) {
            throw new IllegalArgumentException("Les durées doivent respecter pvp < assault < sudden-death < force-end.");
        }
    }
    public GameState targetAt(int elapsedSeconds) {
        if (elapsedSeconds >= forceEndAt) return GameState.ENDING;
        if (elapsedSeconds >= suddenDeathAt) return GameState.SUDDEN_DEATH;
        if (elapsedSeconds >= assaultAt) return GameState.ASSAULT;
        if (elapsedSeconds >= pvpAt) return GameState.PVP;
        return GameState.PREPARATION;
    }
}
