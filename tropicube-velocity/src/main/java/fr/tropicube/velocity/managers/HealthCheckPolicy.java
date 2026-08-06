package fr.tropicube.velocity.managers;

import fr.tropicube.docker.model.ServerInstance;

/** Règles temporelles pures utilisées par la surveillance des instances actives. */
final class HealthCheckPolicy {

    private HealthCheckPolicy() {
    }

    static boolean isMonitored(ServerInstance.Status status) {
        return status == ServerInstance.Status.GAME_WAITING
                || status == ServerInstance.Status.GAME_STARTING
                || status == ServerInstance.Status.GAME_PLAYING
                || status == ServerInstance.Status.GAME_ENDING;
    }

    static boolean isStale(long lastHealthyAt, long now, long timeoutSeconds) {
        if (timeoutSeconds <= 0) throw new IllegalArgumentException("timeoutSeconds doit être strictement positif");
        return now >= lastHealthyAt && now - lastHealthyAt >= timeoutSeconds;
    }
}
