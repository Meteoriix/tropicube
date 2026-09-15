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
        assertEquals(4, KingdomAllocator.kingdomCount(16, 4, 5));
        assertThrows(IllegalArgumentException.class, () -> KingdomAllocator.kingdomCount(17, 4, 4));
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
    @Test void allocationUsesTheSelectedMapLayout() {
        List<KingdomAllocator.PlayerPreference> players = new ArrayList<>();
        for (int index = 0; index < 8; index++) players.add(new KingdomAllocator.PlayerPreference(new UUID(0, index), null));
        Map<UUID, KingdomId> result = new KingdomAllocator().allocate(players, List.of(KingdomId.GREEN, KingdomId.ORANGE));
        assertEquals(Set.of(KingdomId.GREEN, KingdomId.ORANGE), Set.copyOf(result.values()));
        assertThrows(IllegalArgumentException.class,
                () -> new KingdomAllocator().allocate(players, List.of(KingdomId.BLUE, KingdomId.BLUE)));
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
    @Test void winnerCalculationSupportsTiesAndAdministrativeAbortSemantics() {
        VictoryRules rules = new VictoryRules();
        UUID session = UUID.randomUUID();
        GameResult unique = rules.lastKingdom(session, Map.of(KingdomId.BLUE, 2, KingdomId.RED, 0));
        assertEquals(EndCause.LAST_KINGDOM, unique.cause());
        assertEquals(Set.of(KingdomId.BLUE), unique.winners());
        GameResult tie = rules.timeLimit(session, Map.of(KingdomId.BLUE, 2, KingdomId.RED, 2, KingdomId.GREEN, 1));
        assertEquals(EndCause.DRAW, tie.cause());
        assertEquals(Set.of(KingdomId.BLUE, KingdomId.RED), tie.winners());
    }
    @Test void mapVoteIsOneVotePerPlayerAndDeterministicOnTies(){
        MapVote vote=new MapVote();UUID first=new UUID(1,1),second=new UUID(2,2);
        vote.vote(first,"yeti");vote.vote(first,"cactus");vote.vote(second,"yeti");
        assertEquals(1,vote.count("cactus"));assertEquals(1,vote.count("yeti"));
        assertEquals("cactus",vote.winner("fallback"));
    }
    @Test void legacyKnockbackIsBoundedAndAttackDamageValidated(){
        assertEquals(7.0,LegacyCombatRules.attackDamage(7.0));
        assertThrows(IllegalArgumentException.class,()->LegacyCombatRules.attackDamage(-1));
        assertEquals(8.0,LegacyCombatRules.attackDamage(org.bukkit.Material.DIAMOND_SWORD,7.0));
        assertEquals(6.0,LegacyCombatRules.attackDamage(org.bukkit.Material.DIAMOND_AXE,9.0));
        var knockback=LegacyCombatRules.knockback(0,0,0,1,0,true);
        assertEquals(.5,knockback.x());assertEquals(.4,knockback.y());
    }
    @Test void disconnectedPlayerRemainsARespawnEligibleSurvivor(){
        PlayerSession player=new PlayerSession(UUID.randomUUID(),KingdomId.BLUE);
        player.state(PlayerLifeState.OFFLINE);
        assertTrue(player.surviving());
        player.state(PlayerLifeState.ELIMINATED);
        assertFalse(player.surviving());
    }
}
