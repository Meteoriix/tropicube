package fr.tropicube.velocity.managers;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** Registre concurrent garantissant une seule création en cours par clé. */
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
