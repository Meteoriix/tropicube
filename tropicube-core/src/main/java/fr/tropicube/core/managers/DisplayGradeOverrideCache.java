package fr.tropicube.core.managers;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores temporary display identities without changing a player's permissions.
 * Entries are local to a backend session and are restored from the active Redis
 * nick identity whenever Velocity connects the player to that backend.
 */
final class DisplayGradeOverrideCache {

    record DisplayOverride(String name, String gradeName) {}

    private final Map<UUID, DisplayOverride> overrides = new ConcurrentHashMap<>();

    void put(UUID uuid, String name, String gradeName) {
        if (uuid == null) throw new IllegalArgumentException("uuid is required");
        if (name == null || name.isBlank() || gradeName == null || gradeName.isBlank()) {
            overrides.remove(uuid);
            return;
        }
        overrides.put(uuid, new DisplayOverride(
                name.trim(), gradeName.trim().toUpperCase(Locale.ROOT)));
    }

    Optional<DisplayOverride> get(UUID uuid) {
        return Optional.ofNullable(overrides.get(uuid));
    }

    DisplayOverride resolve(UUID uuid, String fallbackName, String fallbackGradeName) {
        return overrides.getOrDefault(uuid, new DisplayOverride(fallbackName, fallbackGradeName));
    }

    void remove(UUID uuid) {
        overrides.remove(uuid);
    }
}
