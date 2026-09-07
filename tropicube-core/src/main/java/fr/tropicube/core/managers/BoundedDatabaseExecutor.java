package fr.tropicube.core.managers;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/** Bounded SQL admission: callers never wait for queue space, including the Paper thread. */
final class BoundedDatabaseExecutor {
    private final ThreadPoolExecutor executor;
    private final Set<CompletableFuture<?>> pending = ConcurrentHashMap.newKeySet();

    BoundedDatabaseExecutor(int concurrency, int queueCapacity) {
        if (concurrency < 1 || queueCapacity < 1) throw new IllegalArgumentException("SQL concurrency/queue must be positive");
        executor = new ThreadPoolExecutor(concurrency, concurrency, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity), Thread.ofVirtual().name("tropicube-sql-", 0).factory(),
                new ThreadPoolExecutor.AbortPolicy());
    }

    <T> CompletableFuture<T> submit(Supplier<T> operation) {
        CompletableFuture<T> result = new CompletableFuture<>();
        pending.add(result);
        result.whenComplete((value, failure) -> pending.remove(result));
        try {
            executor.execute(() -> {
                if (result.isDone()) return;
                try { result.complete(operation.get()); }
                catch (Throwable failure) { result.completeExceptionally(failure); }
            });
        } catch (RejectedExecutionException failure) {
            result.completeExceptionally(failure);
        }
        return result;
    }

    int activeCount() { return executor.getActiveCount(); }
    int queuedCount() { return executor.getQueue().size(); }

    /** Must run on the shutdown worker. Every abandoned future receives a terminal failure. */
    void close(Duration timeout) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(timeout.toMillis(), TimeUnit.MILLISECONDS)) executor.shutdownNow();
        } catch (InterruptedException failure) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        } finally {
            pending.forEach(future -> future.completeExceptionally(new RejectedExecutionException("Database shutdown")));
        }
    }
}
