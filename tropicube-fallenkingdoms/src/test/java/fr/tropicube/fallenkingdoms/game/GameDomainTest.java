package fr.tropicube.fallenkingdoms.game;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GameDomainTest {
    @Test void publicKingdomCountsRespectContract() {
        assertThrows(IllegalArgumentException.class, () -> KingdomAllocator.kingdomCount(7));
        assertEquals(2, KingdomAllocator.kingdomCount(8));
        assertEquals(3, KingdomAllocator.kingdomCount(13));
        assertEquals(4, KingdomAllocator.kingdomCount(19));
        assertEquals(5, KingdomAllocator.kingdomCount(25));
        assertThrows(IllegalArgumentException.class, () -> KingdomAllocator.kingdomCount(31));
    }
    @Test void allocationIsBalancedAndDeterministic() {
        List<KingdomAllocator.PlayerPreference> players = new ArrayList<>();
        for (int i = 0; i < 13; i++) players.add(new KingdomAllocator.PlayerPreference(new UUID(0, i), KingdomId.BLUE));
        KingdomAllocator allocator = new KingdomAllocator();
        Map<UUID, KingdomId> first = allocator.allocate(players), second = allocator.allocate(players);
        assertEquals(first, second);
        assertEquals(3, first.values().stream().distinct().count());
        Map<KingdomId, Long> sizes = first.values().stream().collect(java.util.stream.Collectors.groupingBy(v -> v, java.util.stream.Collectors.counting()));
        assertTrue(sizes.values().stream().allMatch(size -> size >= 4 && size <= 5));
    }
    @Test void timelineAndStateMachineHaveExactBoundaries() {
        PhaseTimeline timeline = new PhaseTimeline(900, 1500, 4500, 5400);
        assertEquals(GameState.PREPARATION, timeline.targetAt(899)); assertEquals(GameState.PVP, timeline.targetAt(900));
        assertEquals(GameState.ASSAULT, timeline.targetAt(1500)); assertEquals(GameState.SUDDEN_DEATH, timeline.targetAt(4500));
        assertEquals(GameState.ENDING, timeline.targetAt(5400));
        GameStateMachine machine = new GameStateMachine();
        assertFalse(machine.transitionTo(GameState.ASSAULT)); assertTrue(machine.transitionTo(GameState.COUNTDOWN));
        assertTrue(machine.transitionTo(GameState.PREPARATION)); assertTrue(machine.transitionTo(GameState.PREPARATION));
    }
    @Test void heartCannotBeDamagedBeforeAssaultOrByTnt() {
        Heart heart = new Heart(KingdomId.BLUE, 500);
        assertEquals(0, heart.damage(KingdomId.RED, 10, false)); heart.makeVulnerable();
        assertEquals(0, heart.damage(KingdomId.BLUE, 10, false)); assertEquals(0, heart.damage(KingdomId.RED, 10, true));
        assertEquals(500, heart.damage(KingdomId.RED, 600, false)); assertEquals(HeartState.DESTROYED, heart.state());
    }
}
