package fr.tropicube.sheepwars.competitive;

import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeasonRewardCatalogTest {
    @Test void everyRankHasAComfortReward() throws Exception {
        File file = new File(getClass().getResource("/season-rewards.yml").toURI());
        SeasonRewardCatalog catalog = SeasonRewardCatalog.load(file);
        assertEquals(1, catalog.version());
        for (RankTier tier : RankTier.values()) {
            assertTrue(catalog.reward(tier).currency() >= 0);
            assertTrue(catalog.reward(tier).title());
            assertTrue(catalog.reward(tier).badge());
        }
    }
}
