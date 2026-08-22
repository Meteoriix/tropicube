package fr.tropicube.sheepwars.competitive;

/** A shared 4v4/8v8 rating with player-specific uncertainty and placement state. */
public record CompetitiveRating(double value, double uncertainty, int placementsRemaining) {
    public CompetitiveRating {
        if (!Double.isFinite(value) || !Double.isFinite(uncertainty) || uncertainty < 50 || uncertainty > 350) {
            throw new IllegalArgumentException("Cote ou incertitude invalide");
        }
        if (placementsRemaining < 0 || placementsRemaining > 5) {
            throw new IllegalArgumentException("placementsRemaining doit être compris entre 0 et 5");
        }
    }

    public static CompetitiveRating initial() { return new CompetitiveRating(1500, 350, 5); }
    public RankTier tier() { return RankTier.forRating(value); }
}
