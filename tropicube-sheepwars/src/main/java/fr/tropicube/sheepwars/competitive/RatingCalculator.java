package fr.tropicube.sheepwars.competitive;

/** Team-result rating update where individual history only controls uncertainty. */
public final class RatingCalculator {
    private RatingCalculator() {}

    public record Update(CompetitiveRating rating, double delta) {}

    public static Update update(CompetitiveRating current, double opponentAverage, double teamScore) {
        if (!Double.isFinite(opponentAverage) || teamScore < 0 || teamScore > 1) {
            throw new IllegalArgumentException("Résultat classé invalide");
        }
        double expected = 1.0 / (1.0 + Math.pow(10.0, (opponentAverage - current.value()) / 400.0));
        double confidenceFactor = 0.75 + current.uncertainty() / 350.0;
        double placementFactor = current.placementsRemaining() > 0 ? 1.35 : 1.0;
        double delta = 24.0 * confidenceFactor * placementFactor * (teamScore - expected);
        double nextValue = Math.max(0, current.value() + delta);
        double nextUncertainty = Math.max(50, current.uncertainty() * (current.placementsRemaining() > 0 ? 0.82 : 0.96));
        int placements = Math.max(0, current.placementsRemaining() - 1);
        return new Update(new CompetitiveRating(nextValue, nextUncertainty, placements), delta);
    }

    /** Starts a season with a soft reset around the neutral 1500 rating. */
    public static CompetitiveRating softReset(CompetitiveRating previous) {
        return new CompetitiveRating(1500 + (previous.value() - 1500) * 0.5,
                Math.min(350, Math.max(250, previous.uncertainty() * 1.5)), 5);
    }
}
