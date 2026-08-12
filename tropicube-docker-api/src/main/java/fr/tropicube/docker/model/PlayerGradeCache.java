package fr.tropicube.docker.model;

import java.util.Collection;
import java.util.UUID;

/** Shared Redis contract for a player's current network rank. */
public final class PlayerGradeCache {

    /** Maximum cache duration, renewed by Core on loading and grade changes. */
    public static final int TTL_SECONDS = 86_400;
    private static final String KEY_PREFIX = "player:grade:";

    private PlayerGradeCache() {
    }

    /** Constructs a player's canonical Redis key. */
    public static String key(UUID uuid) {
        return KEY_PREFIX + uuid;
    }

    /** Indicates whether the cached grade belongs to the configured list, regardless of case. */
    public static boolean isAllowed(String grade, Collection<String> allowedGrades) {
        if (grade == null || allowedGrades == null) return false;
        String normalizedGrade = grade.trim();
        return !normalizedGrade.isEmpty() && allowedGrades.stream()
                .filter(allowed -> allowed != null && !allowed.isBlank())
                .map(String::trim)
                .anyMatch(normalizedGrade::equalsIgnoreCase);
    }
}
