package fr.tropicube.sheepwars.competitive;

import fr.tropicube.sheepwars.player.PlayerClass;

import java.util.Map;

/** Configurable per-team limits for competitive class composition. */
public final class RoleLimitPolicy {
    private final Map<PlayerClass, Integer> limits;

    public RoleLimitPolicy(Map<PlayerClass, Integer> limits) {
        this.limits = Map.copyOf(limits);
        if (limits.entrySet().stream().anyMatch(entry -> entry.getKey() == PlayerClass.NONE || entry.getValue() < 1)) {
            throw new IllegalArgumentException("Limites de rôle invalides");
        }
    }

    public boolean accepts(PlayerClass role, long currentPlayersWithRole) {
        if (role == PlayerClass.NONE) return true;
        return currentPlayersWithRole < limits.getOrDefault(role, Integer.MAX_VALUE);
    }
}
