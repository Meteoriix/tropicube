package fr.tropicube.core.managers;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class BoundedDatabaseExecutorTest {
    @Test void refusesOverloadAndCompletesQueuedWork() throws Exception {
        var executor = new BoundedDatabaseExecutor(1, 1);
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try {
            var first = executor.submit(() -> {
                started.countDown();
                try { release.await(); } catch (InterruptedException error) { Thread.currentThread().interrupt(); }
                return 1;
            });
            assertTrue(started.await(2, TimeUnit.SECONDS));
            var queued = executor.submit(() -> 2);
            assertTrue(executor.submit(() -> 3).isCompletedExceptionally());
            release.countDown();
            assertEquals(1, first.get(2, TimeUnit.SECONDS));
            assertEquals(2, queued.get(2, TimeUnit.SECONDS));
        } finally { release.countDown(); executor.close(Duration.ofSeconds(2)); }
        assertTrue(executor.submit(() -> 4).isCompletedExceptionally());
    }

    @Test void forcedShutdownTerminatesQueuedFutures() throws Exception {
        var executor = new BoundedDatabaseExecutor(1, 1);
        var started = new CountDownLatch(1);
        var first = executor.submit(() -> {
            started.countDown();
            try { new CountDownLatch(1).await(); } catch (InterruptedException expected) { Thread.currentThread().interrupt(); }
            return 1;
        });
        assertTrue(started.await(2, TimeUnit.SECONDS));
        var queued = executor.submit(() -> 2);
        executor.close(Duration.ZERO);
        assertTrue(first.isDone());
        assertTrue(queued.isCompletedExceptionally());
    }

    @Test void rejectsInconsistentPoolConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> new DatabaseOptions(2, 3, 1000, 1000, 2, 10, 10));
        assertThrows(IllegalArgumentException.class, () -> new DatabaseOptions(2, 1, 1000, 1000, 3, 10, 10));
    }
}
