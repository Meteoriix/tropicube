package fr.tropicube.core.network;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class PrivateInputRegistryTest {
    @Test void replacementInvalidatesOldTimeoutAndOnlyOneAnswerCanWin() {
        var registry = new PrivateInputRegistry<Object>();
        UUID id = UUID.randomUUID();
        Object old = new Object(), current = new Object();
        registry.put(id, old);
        registry.put(id, current);
        assertFalse(registry.remove(id, old));
        AtomicInteger winners = new AtomicInteger();
        var operations = java.util.stream.IntStream.range(0, 50).mapToObj(ignored -> CompletableFuture.runAsync(() -> {
            if (registry.remove(id, current)) winners.incrementAndGet();
        })).toArray(CompletableFuture[]::new);
        CompletableFuture.allOf(operations).join();
        assertEquals(1, winners.get());
        assertNull(registry.take(id));
    }

    @Test void shutdownAndCancellationDiscardPendingInputs() {
        var registry = new PrivateInputRegistry<String>();
        UUID id = UUID.randomUUID();
        registry.put(id, "name");
        assertEquals("name", registry.take(id));
        registry.put(id, "tag");
        registry.clear();
        assertNull(registry.get(id));
    }
}
