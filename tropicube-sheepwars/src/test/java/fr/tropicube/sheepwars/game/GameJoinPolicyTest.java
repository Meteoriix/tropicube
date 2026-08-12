package fr.tropicube.sheepwars.game;

import org.junit.jupiter.api.Test;

import static fr.tropicube.sheepwars.game.GameJoinPolicy.Admission.PARTICIPANT;
import static fr.tropicube.sheepwars.game.GameJoinPolicy.Admission.REJECTED;
import static fr.tropicube.sheepwars.game.GameJoinPolicy.Admission.SPECTATOR;
import static org.junit.jupiter.api.Assertions.assertEquals;

class GameJoinPolicyTest {

    @Test
    void admitsPlayersBeforeTheMatchStarts() {
        assertEquals(PARTICIPANT, GameJoinPolicy.admissionFor(GameState.WAITING));
        assertEquals(PARTICIPANT, GameJoinPolicy.admissionFor(GameState.STARTING));
    }

    @Test
    void admitsOnlySpectatorsDuringTheMatch() {
        assertEquals(SPECTATOR, GameJoinPolicy.admissionFor(GameState.PLAYING));
    }

    @Test
    void rejectsPlayersOnceTheMatchIsEnding() {
        assertEquals(REJECTED, GameJoinPolicy.admissionFor(GameState.ENDING));
        assertEquals(REJECTED, GameJoinPolicy.admissionFor(GameState.ENDED));
    }
}
