package fr.tropicube.velocity.managers;

import fr.tropicube.docker.client.RedisManager;
import fr.tropicube.docker.model.AccessPolicy;
import fr.tropicube.docker.model.PlayerAccessProfile;
import org.slf4j.Logger;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Non-blocking local authorization cache fed by Core through Redis. */
public final class AccessProfileCache {
    private static final String EVENT_PREFIX = "ACCESS_CHANGED:";
    private final RedisManager redis;
    private final Logger logger;
    private final AccessPolicy policy;
    private final ConcurrentHashMap<UUID, PlayerAccessProfile> profiles = new ConcurrentHashMap<>();
    private final Consumer<String> eventHandler = this::onPlayerEvent;

    public AccessProfileCache(RedisManager redis, Logger logger, AccessPolicy policy) {
        this.redis = redis;
        this.logger = logger;
        this.policy = policy;
        redis.subscribeToPlayerEvents(eventHandler);
    }

    public PlayerAccessProfile get(UUID uuid) {
        return profiles.getOrDefault(uuid, PlayerAccessProfile.none());
    }

    public boolean hasPermission(UUID uuid, String permission) {
        return policy.hasPermission(get(uuid), permission);
    }

    /** Loads Redis away from Velocity event loops; missing or corrupt data fails closed. */
    public CompletableFuture<PlayerAccessProfile> refresh(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> PlayerAccessProfile.parse(
                        redis.get(PlayerAccessProfile.key(uuid))).orElse(PlayerAccessProfile.none()))
                .thenApply(profile -> {
                    profiles.compute(uuid, (_, current) -> current == null || profile.revision() >= current.revision()
                            ? profile : current);
                    return get(uuid);
                }).exceptionally(error -> {
                    logger.warn("Impossible de charger le profil d'accès de {}", uuid, error);
                    profiles.remove(uuid);
                    return PlayerAccessProfile.none();
                });
    }

    public void unload(UUID uuid) { profiles.remove(uuid); }

    private void onPlayerEvent(String message) {
        if (!message.startsWith(EVENT_PREFIX)) return;
        String payload = message.substring(EVENT_PREFIX.length());
        int separator = payload.indexOf(':');
        if (separator < 0) return;
        try {
            UUID uuid = UUID.fromString(payload.substring(0, separator));
            PlayerAccessProfile.parse(payload.substring(separator + 1)).ifPresent(profile ->
                    profiles.compute(uuid, (_, current) -> current == null || profile.revision() > current.revision()
                            ? profile : current));
        } catch (IllegalArgumentException ignored) {
            logger.warn("Événement ACCESS_CHANGED invalide: {}", message);
        }
    }
}
