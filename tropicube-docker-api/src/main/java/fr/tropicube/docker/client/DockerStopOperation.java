package fr.tropicube.docker.client;

import java.util.Objects;
import java.util.function.BooleanSupplier;

/** Reconciles a shutdown Docker command whose HTTP response may be lost. */
final class DockerStopOperation {

    enum Result {
        COMPLETED,
        ALREADY_STOPPED,
        COMPLETED_AFTER_RETRY
    }

    private DockerStopOperation() {
    }

    /**
     * Performs an idempotent shutdown and checks the actual state of the container before concluding a failure.
     * Docker may have completed the shutdown while its HTTP proxy closes the connection without response.
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
