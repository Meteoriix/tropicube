package fr.tropicube.velocity.managers;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** Concurrent register guaranteeing only one creation in progress per key. */
final class InFlightCreationRegistry<K, V> {

    private final ConcurrentHashMap<K, CompletableFuture<V>> creations = new ConcurrentHashMap<>();

    CompletableFuture<V> getOrCreate(K key, Supplier<CompletableFuture<V>> factory) {
        return creations.computeIfAbsent(key, _ -> factory.get());
    }

    boolean remove(K key, CompletableFuture<V> expected) {
        return creations.remove(key, expected);
    }

    void clear() {
        creations.clear();
    }
}
