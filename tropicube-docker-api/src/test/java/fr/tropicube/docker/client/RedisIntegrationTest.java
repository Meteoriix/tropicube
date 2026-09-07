package fr.tropicube.docker.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.junit.jupiter.api.Assertions.*;

/** Real Redis expiry and subscription reconnection checks on a disposable instance. */
@EnabledIfEnvironmentVariable(named = "TROPICUBE_TEST_REDIS_PORT", matches = "[0-9]+")
class RedisIntegrationTest {
    @Test void expiresStateAndReconnectsWithANewClient() throws Exception {
        int port = Integer.parseInt(System.getenv("TROPICUBE_TEST_REDIS_PORT"));
        RedisManager first = new RedisManager("127.0.0.1", port, "integration-only");
        try {
            first.initialize();
            first.set("integration:expiry", "value", 1);
            assertEquals("value", first.get("integration:expiry"));
        } finally { first.close(); }
        Thread.sleep(1200);
        RedisManager reconnected = new RedisManager("127.0.0.1", port, "integration-only");
        try {
            reconnected.initialize();
            assertNull(reconnected.get("integration:expiry"));
            var received = new java.util.concurrent.CompletableFuture<String>();
            reconnected.subscribeToPlayerEvents(received::complete);
            long end = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
            while (!received.isDone() && System.nanoTime() < end) {
                reconnected.publishPlayerEvent("INTEGRATION", "probe");
                Thread.sleep(50);
            }
            assertTrue(received.get(1, java.util.concurrent.TimeUnit.SECONDS).contains("probe"));
        } finally { reconnected.close(); reconnected.close(); }
    }
}
