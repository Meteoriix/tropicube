package fr.tropicube.velocity.managers;

import fr.tropicube.docker.model.ServerInstance;

import java.util.Objects;

/** Reversible transition of an instance to {@link ServerInstance.Status#STOPPING}. */
final class InstanceStopAttempt {

    private final ServerInstance instance;
    private final ServerInstance.Status previousStatus;

    private InstanceStopAttempt(ServerInstance instance, ServerInstance.Status previousStatus) {
        this.instance = instance;
        this.previousStatus = previousStatus;
    }

    /**
     * Prepares for shutdown and stores the state to restore if Docker refuses the command.
     *
     * @return the attempt created, or {@code null} if a shutdown is already in progress or completed
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
