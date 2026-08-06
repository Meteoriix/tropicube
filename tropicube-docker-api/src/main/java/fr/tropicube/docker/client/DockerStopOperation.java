package fr.tropicube.docker.client;

import java.util.Objects;
import java.util.function.BooleanSupplier;

/** Réconcilie une commande Docker d'arrêt dont la réponse HTTP peut être perdue. */
final class DockerStopOperation {

    enum Result {
        COMPLETED,
        ALREADY_STOPPED,
        COMPLETED_AFTER_RETRY
    }

    private DockerStopOperation() {
    }

    /**
     * Exécute un arrêt idempotent et vérifie l'état réel du conteneur avant de conclure à un échec.
     * Docker peut avoir terminé l'arrêt alors que son proxy HTTP ferme la connexion sans réponse.
     */
    static Result execute(Runnable stopCommand, BooleanSupplier runningProbe) {
        Objects.requireNonNull(stopCommand, "stopCommand");
        Objects.requireNonNull(runningProbe, "runningProbe");

        RuntimeException firstFailure;
        try {
            stopCommand.run();
            return Result.COMPLETED;
        } catch (RuntimeException failure) {
            firstFailure = failure;
        }

        if (isConfirmedStopped(runningProbe, firstFailure)) {
            return Result.ALREADY_STOPPED;
        }

        try {
            stopCommand.run();
            return Result.COMPLETED_AFTER_RETRY;
        } catch (RuntimeException retryFailure) {
            retryFailure.addSuppressed(firstFailure);
            if (isConfirmedStopped(runningProbe, retryFailure)) {
                return Result.ALREADY_STOPPED;
            }
            throw retryFailure;
        }
    }

    private static boolean isConfirmedStopped(BooleanSupplier runningProbe, RuntimeException commandFailure) {
        try {
            return !runningProbe.getAsBoolean();
        } catch (RuntimeException probeFailure) {
            commandFailure.addSuppressed(probeFailure);
            return false;
        }
    }
}
