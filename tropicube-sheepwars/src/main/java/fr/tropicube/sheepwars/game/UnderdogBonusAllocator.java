package fr.tropicube.sheepwars.game;

import java.util.ArrayList;
import java.util.List;

/** Pure distribution rule for starting sheep granted to an undersized team. */
public final class UnderdogBonusAllocator {

    private UnderdogBonusAllocator() {
    }

    /** Returns the bonus count for each player, distributed round-robin under a per-player cap. */
    public static List<Integer> allocate(int playerCount, int missingPlayers,
                                         int sheepPerMissingPlayer, int maximumPerPlayer) {
        if (playerCount <= 0 || missingPlayers <= 0 || sheepPerMissingPlayer <= 0 || maximumPerPlayer <= 0) {
            return List.of();
        }
        List<Integer> result = new ArrayList<>(java.util.Collections.nCopies(playerCount, 0));
        int pool = missingPlayers * sheepPerMissingPlayer;
        for (int bonus = 0; bonus < pool; bonus++) {
            int playerIndex = bonus % playerCount;
            if (result.get(playerIndex) >= maximumPerPlayer) break;
            result.set(playerIndex, result.get(playerIndex) + 1);
        }
        return List.copyOf(result);
    }
}
