package fr.tropicube.lobby.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GradeUpgradePricingTest {

    @Test
    void formatsBalancesWithoutNarrowingToInteger() {
        assertEquals("100.0B", VipShopGUI.formatCoins(100_000_000_000D));
        assertEquals("2.1B", VipShopGUI.formatCoins(2_147_483_647D));
        assertEquals("1.5M", VipShopGUI.formatCoins(1_500_000D));
        assertEquals("100", VipShopGUI.formatCoins(100D));
    }
    @Test
    void deductsTheAlreadyPurchasedCatalogValue() {
        assertEquals(10_000, GradeUpgradePricing.difference(5_000, 15_000));
        assertEquals(25_000, GradeUpgradePricing.difference(15_000, 40_000));
    }

    @Test
    void usesFullPriceForUnpricedBaseGradeAndNeverGoesNegative() {
        assertEquals(5_000, GradeUpgradePricing.difference(-1, 5_000));
        assertEquals(0, GradeUpgradePricing.difference(40_000, 15_000));
        assertEquals(-1, GradeUpgradePricing.difference(5_000, -1));
    }
}
