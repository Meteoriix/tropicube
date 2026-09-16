package fr.tropicube.sheepwars.game;

/** Immutable environmental rules applied while a SheepWars map is active. */
public record MapHazards(
        boolean voidKillEnabled,
        boolean waterPoisonEnabled,
        int waterPoisonDurationTicks,
        int waterPoisonAmplifier
) {
    public static MapHazards defaults() {
        return new MapHazards(true, false, 20, 0);
    }
}
