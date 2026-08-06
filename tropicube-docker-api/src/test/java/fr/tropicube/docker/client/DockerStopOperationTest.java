package fr.tropicube.docker.client;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DockerStopOperationTest {

    @Test
    void acceptsLostResponseWhenContainerIsAlreadyStopped() {
        AtomicInteger attempts = new AtomicInteger();

        DockerStopOperation.Result result = DockerStopOperation.execute(() -> {
            attempts.incrementAndGet();
            throw new RuntimeException("response lost");
        }, () -> false);

        assertEquals(DockerStopOperation.Result.ALREADY_STOPPED, result);
        assertEquals(1, attempts.get());
    }

    @Test
    void retriesOnceWhenContainerIsStillRunning() {
        AtomicInteger attempts = new AtomicInteger();

        DockerStopOperation.Result result = DockerStopOperation.execute(() -> {
            if (attempts.incrementAndGet() == 1) throw new RuntimeException("stale connection");
        }, () -> true);

        assertEquals(DockerStopOperation.Result.COMPLETED_AFTER_RETRY, result);
        assertEquals(2, attempts.get());
    }

    @Test
    void reconcilesSecondLostResponseAgainstContainerState() {
        AtomicInteger attempts = new AtomicInteger();

        DockerStopOperation.Result result = DockerStopOperation.execute(() -> {
            attempts.incrementAndGet();
            throw new RuntimeException("response lost");
        }, () -> attempts.get() < 2);

        assertEquals(DockerStopOperation.Result.ALREADY_STOPPED, result);
        assertEquals(2, attempts.get());
    }

    @Test
    void propagatesFailureWhenContainerRemainsRunning() {
        RuntimeException error = assertThrows(RuntimeException.class,
                () -> DockerStopOperation.execute(
                        () -> { throw new RuntimeException("daemon unavailable"); },
                        () -> true));

        assertEquals("daemon unavailable", error.getMessage());
        assertEquals(1, error.getSuppressed().length);
    }
}
