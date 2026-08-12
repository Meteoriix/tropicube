package fr.tropicube.velocity.commands;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Tracks each player's active nick generation and invalidates obsolete callbacks. */
final class NickRequestRegistry {

    private final ConcurrentMap<UUID, Object> requests = new ConcurrentHashMap<>();

    Object begin(UUID uuid) {
        Object token = new Object();
        return requests.putIfAbsent(uuid, token) == null ? token : null;
    }

    boolean complete(UUID uuid, Object token) {
        return requests.remove(uuid, token);
    }

    boolean cancel(UUID uuid) {
        return requests.remove(uuid) != null;
    }
}
