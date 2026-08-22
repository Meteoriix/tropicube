package fr.tropicube.sheepwars.competitive;

/** VALORANT-compatible rank names; only the thresholds are specific to Tropicube. */
public enum RankTier {
    IRON(0), BRONZE(900), SILVER(1100), GOLD(1300), PLATINUM(1500),
    DIAMOND(1700), ASCENDANT(1900), IMMORTAL(2100), RADIANT(2400);

    private final int minimumRating;

    RankTier(int minimumRating) { this.minimumRating = minimumRating; }
    public int minimumRating() { return minimumRating; }

    public static RankTier forRating(double rating) {
        RankTier result = IRON;
        for (RankTier tier : values()) if (rating >= tier.minimumRating) result = tier;
        return result;
    }
}
