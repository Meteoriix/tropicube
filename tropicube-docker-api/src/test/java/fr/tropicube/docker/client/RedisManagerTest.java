package fr.tropicube.docker.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RedisManagerTest {

    @Test
    void validatesConnectionSettings() {
        assertThrows(IllegalArgumentException.class, () -> new RedisManager(" ", 6379, null));
        assertThrows(IllegalArgumentException.class, () -> new RedisManager("redis", 0, null));
        assertThrows(IllegalArgumentException.class, () -> new RedisManager("redis", 65_536, null));
    }

    @Test
    void rejectsOperationsBeforeInitializationAndClosesIdempotently() {
        RedisManager manager = new RedisManager("redis", 6379, null);

        assertThrows(IllegalStateException.class, manager::getClient);
        assertDoesNotThrow(manager::close);
        assertDoesNotThrow(manager::close);
        assertThrows(IllegalStateException.class, manager::initialize);
    }

    @Test
    void validatesGenericTtlBeforeAccessingRedis() {
        RedisManager manager = new RedisManager("redis", 6379, null);
        try {
            assertThrows(IllegalArgumentException.class, () -> manager.set("key", "value", 0));
            assertThrows(IllegalArgumentException.class,
                    () -> manager.reserveUnlessBlocked("reservation", "owner", "value", 0));
        } finally {
            manager.close();
        }
    }
}
