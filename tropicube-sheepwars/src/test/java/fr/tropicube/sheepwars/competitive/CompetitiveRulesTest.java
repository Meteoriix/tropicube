package fr.tropicube.sheepwars.competitive;

import fr.tropicube.sheepwars.player.PlayerClass;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CompetitiveRulesTest {
    @Test
    void queuesShareRatingRulesButKeepTheirCapacities() {
        assertEquals(8, SheepWarsMode.RANKED_4V4.maximumPlayers());
        assertEquals(16, SheepWarsMode.RANKED_8V8.maximumPlayers());
        assertTrue(SheepWarsMode.RANKED_4V4.ranked());
        assertFalse(SheepWarsMode.QUICK_PLAY.ranked());
        assertTrue(PartyQueuePolicy.accepts(SheepWarsMode.RANKED_4V4, 2));
        assertFalse(PartyQueuePolicy.accepts(SheepWarsMode.RANKED_4V4, 3));
    }

    @Test
    void teamResultAlwaysDeterminesRatingDirection() {
        CompetitiveRating initial = CompetitiveRating.initial();
        RatingCalculator.Update win = RatingCalculator.update(initial, 1500, 1);
        RatingCalculator.Update loss = RatingCalculator.update(initial, 1500, 0);
        assertTrue(win.delta() > 0);
        assertTrue(loss.delta() < 0);
        assertEquals(4, win.rating().placementsRemaining());
        assertTrue(win.rating().uncertainty() < initial.uncertainty());
    }

    @Test
    void softResetKeepsHalfTheDistanceAndRestartsPlacements() {
        CompetitiveRating reset = RatingCalculator.softReset(new CompetitiveRating(1900, 80, 0));
        assertEquals(1700, reset.value());
        assertEquals(5, reset.placementsRemaining());
        assertEquals(RankTier.DIAMOND, reset.tier());
    }

    @Test
    void matchmakingWindowWidensAndStopsAtItsCeiling() {
        MatchmakingPolicy policy = new MatchmakingPolicy(75, 25, Duration.ofSeconds(15), 500);
        assertEquals(75, policy.rangeAfter(Duration.ZERO));
        assertEquals(125, policy.rangeAfter(Duration.ofSeconds(30)));
        assertEquals(500, policy.rangeAfter(Duration.ofHours(1)));
    }

    @Test
    void roleLimitRejectsOnlyWhenTheTeamLimitIsReached() {
        RoleLimitPolicy policy = new RoleLimitPolicy(Map.of(PlayerClass.DPS, 2));
        assertTrue(policy.accepts(PlayerClass.DPS, 1));
        assertFalse(policy.accepts(PlayerClass.DPS, 2));
        assertTrue(policy.accepts(PlayerClass.SUPPORT, 99));
    }
}
