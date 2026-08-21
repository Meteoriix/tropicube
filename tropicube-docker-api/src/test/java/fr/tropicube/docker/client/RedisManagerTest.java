package fr.tropicube.docker.client;

import fr.tropicube.docker.model.PartyDisconnectResult;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void identifiesEveryKnownInstanceReferenceShape() {
        String id = "instance-id";
        String name = "Sheepwars-deadbeef";

        assertTrue(RedisManager.isInstanceReference("tropicube:host:player", id, id, name));
        assertTrue(RedisManager.isInstanceReference("tropicube:player:server:player", id, id, name));
        assertTrue(RedisManager.isInstanceReference("tropicube:sw:rejoin:player", id, id, name));
        assertTrue(RedisManager.isInstanceReference("tropicube:sw:left-game:player", id, id, name));
        assertTrue(RedisManager.isInstanceReference("tropicube:sw:next-game:source", name, id, name));
        assertTrue(RedisManager.isInstanceReference("tropicube:post-game:player", name + "|SHEEPWARS", id, name));
        assertFalse(RedisManager.isInstanceReference("tropicube:host:other", "other-id", id, name));
        assertFalse(RedisManager.isInstanceReference("tropicube:post-game:other", "|SHEEPWARS", id, name));
    }

    @Test
    void decodesAtomicPartyDisconnectOutcomes() {
        UUID promoted = UUID.randomUUID();

        assertEquals(PartyDisconnectResult.Status.UNCHANGED,
                RedisManager.parsePartyDisconnectResult("ONLINE").status());
        assertEquals(PartyDisconnectResult.Status.REMOVED,
                RedisManager.parsePartyDisconnectResult("LEFT").status());
        assertEquals(PartyDisconnectResult.Status.DISBANDED,
                RedisManager.parsePartyDisconnectResult("DISBANDED").status());
        assertEquals(promoted,
                RedisManager.parsePartyDisconnectResult("PROMOTED:" + promoted).promotedLeaderId());
        assertThrows(IllegalStateException.class,
                () -> RedisManager.parsePartyDisconnectResult("UNKNOWN"));
    }
}
