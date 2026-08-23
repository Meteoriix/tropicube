package fr.tropicube.lobby.gui;

/** Pure pricing rule used by the grade shop. */
final class GradeUpgradePricing {
    private GradeUpgradePricing() { }

    static int difference(int currentCatalogPrice, int targetCatalogPrice) {
        if (targetCatalogPrice < 0) return -1;
        return Math.max(0, targetCatalogPrice - Math.max(0, currentCatalogPrice));
    }
}
