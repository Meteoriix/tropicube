package fr.tropicube.core.managers;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores temporary display-only grades without changing a player's permissions.
 * Entries are local to a backend session and are restored from the active Redis
 * nick identity whenever Velocity connects the player to that backend.
 */
final class DisplayGradeOverrideCache {

    private final Map<UUID, String> overrides = new ConcurrentHashMap<>();

    void put(UUID uuid, String gradeName) {
        if (uuid == null) throw new IllegalArgumentException("uuid is required");
        if (gradeName == null || gradeName.isBlank()) {
            overrides.remove(uuid);
            return;
        }
        overrides.put(uuid, gradeName.trim().toUpperCase(Locale.ROOT));
    }

    Optional<String> get(UUID uuid) {
        return Optional.ofNullable(overrides.get(uuid));
    }

    void remove(UUID uuid) {
        overrides.remove(uuid);
    }
}
