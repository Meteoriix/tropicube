package fr.tropicube.fallenkingdoms.game;

/** States of one immutable Fallen Kingdoms session. */
public enum GameState {
    WAITING, COUNTDOWN, PREPARATION, PVP, ASSAULT, SUDDEN_DEATH, ENDING, ENDED;

    public boolean active() {
        return this == PREPARATION || this == PVP || this == ASSAULT || this == SUDDEN_DEATH;
    }
}
