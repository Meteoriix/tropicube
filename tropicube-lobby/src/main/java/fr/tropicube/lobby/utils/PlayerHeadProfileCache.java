package fr.tropicube.lobby.utils;

import io.papermc.paper.datacomponent.item.ResolvableProfile;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Resolves and caches complete skin profiles without blocking the Paper thread. */
public final class PlayerHeadProfileCache {

    private static final int RESOLUTION_TIMEOUT_SECONDS = 5;

    private final Logger logger;
    private final ConcurrentMap<UUID, CompletableFuture<ResolvableProfile>> profiles = new ConcurrentHashMap<>();

    public PlayerHeadProfileCache(Logger logger) {
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /**
     * Returns a profile containing the current skin texture. Concurrent requests for the same
     * player share one network lookup. Failed or incomplete lookups are evicted so a later menu can retry.
     */
    public CompletableFuture<ResolvableProfile> resolve(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        ResolvableProfile fallback = unresolvedProfile(playerId);
        CompletableFuture<ResolvableProfile> resolved;
        try {
            resolved = profiles.computeIfAbsent(playerId, ignored -> fallback.resolve()
                    .thenApply(profile -> {
                        if (!profile.isComplete()) {
                            throw new CompletionException(
                                    new IllegalStateException("Profil de skin incomplet pour " + playerId));
                        }
                        return ResolvableProfile.resolvableProfile(profile);
                    })
                    .orTimeout(RESOLUTION_TIMEOUT_SECONDS, TimeUnit.SECONDS));
        } catch (RuntimeException exception) {
            logger.log(Level.FINE, "Impossible de démarrer la résolution du skin de " + playerId, exception);
            return CompletableFuture.completedFuture(fallback);
        }
        return resolved.handle((profile, error) -> {
            if (error == null) return profile;
            profiles.remove(playerId, resolved);
            logger.log(Level.FINE, "Impossible de résoudre le skin de " + playerId, error);
            return fallback;
        });
    }

    /** Cancels unresolved lookups and releases cached profiles during plugin shutdown. */
    public void clear() {
        profiles.values().forEach(future -> future.cancel(false));
        profiles.clear();
    }

    private static ResolvableProfile unresolvedProfile(UUID playerId) {
        // Paper 26.2 treats UUID + name without properties as a static partial profile;
        // UUID alone is intentionally required to trigger the server-side dynamic lookup.
        return ResolvableProfile.resolvableProfile()
                .uuid(playerId)
                .build();
    }
}
