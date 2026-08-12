package fr.tropicube.docker.model;

import java.util.Collection;
import java.util.UUID;

/** Contrat Redis partagé pour le grade réseau courant d'un joueur. */
public final class PlayerGradeCache {

    /** Durée maximale du cache, renouvelée par Core au chargement et aux changements de grade. */
    public static final int TTL_SECONDS = 86_400;
    private static final String KEY_PREFIX = "player:grade:";

    private PlayerGradeCache() {
    }

    /** Construit la clé Redis canonique d'un joueur. */
    public static String key(UUID uuid) {
        return KEY_PREFIX + uuid;
    }

    /** Indique si le grade mis en cache appartient à la liste configurée, sans tenir compte de la casse. */
    public static boolean isAllowed(String grade, Collection<String> allowedGrades) {
        if (grade == null || allowedGrades == null) return false;
        String normalizedGrade = grade.trim();
        return !normalizedGrade.isEmpty() && allowedGrades.stream()
                .filter(allowed -> allowed != null && !allowed.isBlank())
                .map(String::trim)
                .anyMatch(normalizedGrade::equalsIgnoreCase);
    }
}
