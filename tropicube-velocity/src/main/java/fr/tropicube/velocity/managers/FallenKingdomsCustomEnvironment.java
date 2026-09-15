package fr.tropicube.velocity.managers;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Validates the only environment variables that a Lobby FK setup may inject. */
public final class FallenKingdomsCustomEnvironment {
    private static final Set<String> ALLOWED = Set.of(
            "FK_AUTO_START",
            "FK_COMBAT_PROFILE",
            "FK_COUNTDOWN_SECONDS",
            "FK_MAX_PLAYERS_PER_KINGDOM",
            "FK_MAX_KINGDOMS",
            "FK_PVP_AT_SECONDS",
            "FK_ASSAULT_AT_SECONDS",
            "FK_SUDDEN_DEATH_AT_SECONDS",
            "FK_FORCE_END_AT_SECONDS",
            "FK_HEART_HEALTH",
            "FK_RESPAWN_DELAY_SECONDS",
            "FK_ENABLED_KITS",
            "FK_RUIN_WAVES",
            "FK_RUIN_RADIUS",
            "FK_RUIN_DESTRUCTION_RATIO");

    private FallenKingdomsCustomEnvironment() { }

    public static Map<String, String> parse(String encoded) {
        if (encoded == null || encoded.isBlank()) return Map.of();
        if (encoded.length() > 2048) throw new IllegalArgumentException("Options FK trop longues");
        Map<String, String> result = new LinkedHashMap<>();
        for (String assignment : encoded.split(";")) {
            String[] pair = assignment.split("=", 2);
            if (pair.length != 2 || !ALLOWED.contains(pair[0])
                    || !pair[1].matches("[A-Za-z0-9_.,-]{1,80}")) {
                throw new IllegalArgumentException("Option FK refusée");
            }
            result.put(pair[0], pair[1]);
        }
        return Map.copyOf(result);
    }
}
