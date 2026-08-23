package fr.tropicube.lobby.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GradeUpgradePricingTest {
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
