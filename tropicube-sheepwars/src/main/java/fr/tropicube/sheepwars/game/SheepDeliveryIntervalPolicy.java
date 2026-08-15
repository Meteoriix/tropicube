package fr.tropicube.sheepwars.game;

/** Selects the delivery interval without coupling the rule to Bukkit. */
final class SheepDeliveryIntervalPolicy {

    private SheepDeliveryIntervalPolicy() {
    }

    static int resolve(boolean customGame, int configuredIntervalSeconds, int playerCount) {
        if (configuredIntervalSeconds <= 0) {
            throw new IllegalArgumentException("L'intervalle configuré doit être positif");
        }
        if (playerCount <= 0) {
            throw new IllegalArgumentException("Le nombre de joueurs doit être positif");
        }
        if (customGame) return configuredIntervalSeconds;
        if (playerCount <= 4) return 10;
        if (playerCount <= 8) return 12;
        return 15;
    }
}
