package fr.tropicube.sheepwars.game;

/** Règles pures déterminant l'activation et le déclenchement du démarrage automatique. */
final class AutoStartPolicy {

    private AutoStartPolicy() {
    }

    static boolean initialValue(boolean customGame, boolean classicDefault, boolean customDefault) {
        return customGame ? customDefault : classicDefault;
    }

    static boolean shouldStart(boolean enabled, GameState state, int playerCount, int minPlayers) {
        return enabled && state == GameState.WAITING && playerCount >= minPlayers;
    }
}
