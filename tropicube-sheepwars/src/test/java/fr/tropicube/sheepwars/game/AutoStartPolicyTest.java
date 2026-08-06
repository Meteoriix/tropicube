package fr.tropicube.sheepwars.game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoStartPolicyTest {

    @Test
    void selectsTheDefaultForTheInstanceType() {
        assertTrue(AutoStartPolicy.initialValue(false, true, false));
        assertFalse(AutoStartPolicy.initialValue(true, true, false));
    }

    @Test
    void startsOnlyWhileWaitingWithEnoughPlayers() {
        assertTrue(AutoStartPolicy.shouldStart(true, GameState.WAITING, 2, 2));
        assertFalse(AutoStartPolicy.shouldStart(false, GameState.WAITING, 2, 2));
        assertFalse(AutoStartPolicy.shouldStart(true, GameState.WAITING, 1, 2));
        assertFalse(AutoStartPolicy.shouldStart(true, GameState.STARTING, 2, 2));
    }
}
