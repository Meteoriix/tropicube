package fr.tropicube.sheepwars.game;

import java.util.Objects;

/** Determines how a player may enter an instance for its current lifecycle state. */
public final class GameJoinPolicy {

    private GameJoinPolicy() {
    }

    public static Admission admissionFor(GameState state) {
        return switch (Objects.requireNonNull(state, "state")) {
            case WAITING, STARTING -> Admission.PARTICIPANT;
            case PLAYING -> Admission.SPECTATOR;
            case ENDING, ENDED -> Admission.REJECTED;
        };
    }

    public enum Admission {
        PARTICIPANT,
        SPECTATOR,
        REJECTED
    }
}
