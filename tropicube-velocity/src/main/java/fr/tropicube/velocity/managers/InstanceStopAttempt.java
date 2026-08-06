package fr.tropicube.velocity.managers;

import fr.tropicube.docker.model.ServerInstance;

import java.util.Objects;

/** Transition réversible d'une instance vers {@link ServerInstance.Status#STOPPING}. */
final class InstanceStopAttempt {

    private final ServerInstance instance;
    private final ServerInstance.Status previousStatus;

    private InstanceStopAttempt(ServerInstance instance, ServerInstance.Status previousStatus) {
        this.instance = instance;
        this.previousStatus = previousStatus;
    }

    /**
     * Prépare l'arrêt et mémorise l'état à restaurer si Docker refuse la commande.
     *
     * @return la tentative créée, ou {@code null} si un arrêt est déjà en cours ou terminé
     */
    static InstanceStopAttempt begin(ServerInstance instance) {
        Objects.requireNonNull(instance, "instance");
        synchronized (instance) {
            if (instance.getStatus() == ServerInstance.Status.STOPPING
                    || instance.getStatus() == ServerInstance.Status.STOPPED) return null;
            ServerInstance.Status previousStatus = Objects.requireNonNull(instance.getStatus(), "instance.status");
            instance.setStatus(ServerInstance.Status.STOPPING);
            return new InstanceStopAttempt(instance, previousStatus);
        }
    }

    void restore() {
        synchronized (instance) {
            instance.setStatus(previousStatus);
        }
    }

    ServerInstance instance() {
        return instance;
    }

    ServerInstance.Status previousStatus() {
        return previousStatus;
    }
}
