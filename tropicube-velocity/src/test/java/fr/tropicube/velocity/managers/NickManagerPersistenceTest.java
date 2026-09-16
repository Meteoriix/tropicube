package fr.tropicube.velocity.managers;

import fr.tropicube.docker.client.RedisManager;
import fr.tropicube.docker.model.NickIdentity;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NickManagerPersistenceTest {

    @Test
    void keepsTheCompleteDisplayIdentityForAReconnect() {
        UUID uuid = UUID.randomUUID();
        InMemoryRedisManager redis = new InMemoryRedisManager();
        try {
            NickManager manager = new NickManager(redis, LoggerFactory.getLogger(getClass()), List.of(), null);
            NickIdentity identity = new NickIdentity("MaskedWolf", "skin", "signature", "PREMIUM");
            redis.set(NickIdentity.key(uuid), identity.toJson(), 10);
            redis.set("nick:original:" + uuid,
                    "{\"n\":\"RealWolf\",\"v\":\"skin\",\"s\":\"signature\"}", 10);

            manager.parkNick(uuid);

            NickManager.NickData restored = manager.getNick(uuid).orElseThrow();
            assertEquals("PREMIUM", restored.displayGrade());
            assertEquals(NickIdentity.TTL_SECONDS, redis.ttl(NickIdentity.key(uuid)));
            assertEquals(NickIdentity.TTL_SECONDS, redis.ttl("nick:original:" + uuid));
        } finally {
            redis.close();
        }
    }

    @Test
    void keepsRecoveryStateUntilTheBackendAcknowledgesNickRemoval() {
        UUID uuid = UUID.randomUUID();
        InMemoryRedisManager redis = new InMemoryRedisManager();
        try {
            NickManager manager = new NickManager(redis, LoggerFactory.getLogger(getClass()), List.of(), null);
            manager.storeNick(uuid, "MaskedWolf", new NickManager.SkinData("skin", "signature"));
            redis.set("nick:original:" + uuid,
                    "{\"n\":\"RealWolf\",\"v\":\"skin\",\"s\":\"signature\"}", 10);

            manager.requestNickClear(uuid);

            assertTrue(manager.getNick(uuid).isPresent());
            assertTrue(manager.getOriginalProfile(uuid).isPresent());
            assertEquals("NICK_CLEAR:" + uuid, redis.lastPlayerEvent);
        } finally {
            redis.close();
        }
    }

    @Test
    void allowsRemovalRetryWhenOnlyTheOriginalProfileRemains() {
        UUID uuid = UUID.randomUUID();
        InMemoryRedisManager redis = new InMemoryRedisManager();
        try {
            NickManager manager = new NickManager(redis, LoggerFactory.getLogger(getClass()), List.of(), null);
            redis.set("nick:original:" + uuid,
                    "{\"n\":\"RealWolf\",\"v\":\"skin\",\"s\":\"signature\"}", 10);

            assertTrue(manager.hasRecoverableNickState(uuid));
        } finally {
            redis.close();
        }
    }

    @Test
    void keepsTheAuthenticatedNameIndependentFromTheNickedSessionProfile() {
        UUID uuid = UUID.randomUUID();
        InMemoryRedisManager redis = new InMemoryRedisManager();
        try {
            NickManager manager = new NickManager(redis, LoggerFactory.getLogger(getClass()), List.of(), null);

            manager.rememberRealName(uuid, "RealWolf");

            assertEquals("RealWolf", manager.realName(uuid, "MaskedWolf"));
            manager.forgetRealName(uuid);
            assertEquals("MaskedWolf", manager.realName(uuid, "MaskedWolf"));
        } finally {
            redis.close();
        }
    }

    private static final class InMemoryRedisManager extends RedisManager {
        private final Map<String, String> values = new HashMap<>();
        private final Map<String, Integer> ttls = new HashMap<>();
        private String lastPlayerEvent;

        private InMemoryRedisManager() {
            super("localhost", 6379, null);
        }

        @Override
        public void set(String key, String value, int ttlSeconds) {
            values.put(key, value);
            ttls.put(key, ttlSeconds);
        }

        @Override
        public String get(String key) {
            return values.get(key);
        }

        @Override
        public void delete(String key) {
            values.remove(key);
            ttls.remove(key);
        }

        @Override
        public void publishPlayerEvent(String eventType, String payload) {
            lastPlayerEvent = eventType + ":" + payload;
        }

        private int ttl(String key) {
            return ttls.getOrDefault(key, -1);
        }
    }
}
