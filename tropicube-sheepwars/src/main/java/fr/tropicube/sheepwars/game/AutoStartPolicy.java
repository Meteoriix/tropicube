package fr.tropicube.sheepwars.game;

/** Pure rules determining the activation and triggering of automatic start. */
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
