package fr.tropicube.velocity.managers;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InFlightCreationRegistryTest {

    @Test
    void sharesOneCreationUntilItIsExplicitlyRemoved() {
        InFlightCreationRegistry<String, String> registry = new InFlightCreationRegistry<>();
        AtomicInteger creations = new AtomicInteger();
        CompletableFuture<String> firstCreation = new CompletableFuture<>();

        CompletableFuture<String> first = registry.getOrCreate("sheepwars", () -> {
            creations.incrementAndGet();
            return firstCreation;
        });
        CompletableFuture<String> second = registry.getOrCreate("sheepwars", () -> {
            creations.incrementAndGet();
            return CompletableFuture.completedFuture("duplicate");
        });

        assertSame(first, second);
        assertEquals(1, creations.get());
        assertTrue(registry.remove("sheepwars", firstCreation));

        CompletableFuture<String> replacement = registry.getOrCreate("sheepwars", () -> {
            creations.incrementAndGet();
            return CompletableFuture.completedFuture("replacement");
        });
        assertEquals("replacement", replacement.join());
        assertEquals(2, creations.get());
    }
}
