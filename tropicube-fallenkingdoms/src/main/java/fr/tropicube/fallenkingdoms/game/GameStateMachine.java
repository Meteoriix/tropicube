package fr.tropicube.fallenkingdoms.game;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

/** Validates state changes before Paper adapters apply their side effects. */
public final class GameStateMachine {
    private static final Map<GameState, EnumSet<GameState>> TRANSITIONS = new EnumMap<>(GameState.class);
    static {
        TRANSITIONS.put(GameState.WAITING, EnumSet.of(GameState.COUNTDOWN, GameState.ENDING));
        TRANSITIONS.put(GameState.COUNTDOWN, EnumSet.of(GameState.WAITING, GameState.PREPARATION, GameState.ENDING));
        TRANSITIONS.put(GameState.PREPARATION, EnumSet.of(GameState.PVP, GameState.ENDING));
        TRANSITIONS.put(GameState.PVP, EnumSet.of(GameState.ASSAULT, GameState.ENDING));
        TRANSITIONS.put(GameState.ASSAULT, EnumSet.of(GameState.SUDDEN_DEATH, GameState.ENDING));
        TRANSITIONS.put(GameState.SUDDEN_DEATH, EnumSet.of(GameState.ENDING));
        TRANSITIONS.put(GameState.ENDING, EnumSet.of(GameState.ENDED));
        TRANSITIONS.put(GameState.ENDED, EnumSet.noneOf(GameState.class));
    }
    private GameState state = GameState.WAITING;
    public GameState state() { return state; }
    /** Repeated requests are harmless; invalid changes are rejected without mutation. */
    public boolean transitionTo(GameState target) {
        if (state == target) return true;
        if (!TRANSITIONS.get(state).contains(target)) return false;
        state = target;
        return true;
    }
}
