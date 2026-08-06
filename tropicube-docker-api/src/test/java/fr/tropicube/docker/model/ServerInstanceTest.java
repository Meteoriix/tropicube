package fr.tropicube.docker.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ServerInstanceTest {

    @Test
    void publicMatchmakingExcludesWhitelistedServers() {
        UUID allowed = UUID.randomUUID();
        ServerInstance instance = readyInstance(true);
        instance.setWhitelistedPlayers(List.of(allowed));

        assertFalse(instance.isJoinable());
        assertTrue(instance.isJoinable(allowed));
        assertFalse(instance.isJoinable(UUID.randomUUID()));
    }

    @Test
    void fullServerIsNotJoinableEvenForWhitelistedPlayer() {
        UUID allowed = UUID.randomUUID();
        ServerInstance instance = readyInstance(true);
        instance.setWhitelistedPlayers(List.of(allowed));
        instance.setOnlinePlayers(instance.getMaxPlayers());

        assertFalse(instance.isJoinable(allowed));
    }

    @Test
    void jsonRoundTripPreservesValidatedState() {
        UUID allowed = UUID.randomUUID();
        ServerInstance source = readyInstance(true);
        source.setContainerId("container-id");
        source.setServerType("LOBBY");
        source.setWhitelistedPlayers(List.of(allowed));

        ServerInstance restored = ServerInstance.fromJson(source.toJson());

        assertEquals(source.getInstanceId(), restored.getInstanceId());
        assertEquals(source.getStatus(), restored.getStatus());
        assertEquals(List.of(allowed), restored.getWhitelistedPlayers());
        assertTrue(restored.isJoinable(allowed));
    }

    @Test
    void rejectsIncompleteRedisPayload() {
        String json = "{\"instanceId\":\"id\",\"templateId\":\"lobby\",\"serverName\":\"Lobby\","
                + "\"port\":25600,\"maxPlayers\":10,\"status\":\"GAME_WAITING\"}";

        assertThrows(IllegalArgumentException.class, () -> ServerInstance.fromJson(json));
    }

    @Test
    void whitelistCollectionCannotBeMutatedThroughGetter() {
        ServerInstance instance = readyInstance(true);

        assertThrows(UnsupportedOperationException.class,
                () -> instance.getWhitelistedPlayers().add(UUID.randomUUID()));
    }

    @Test
    void nullWhitelistIsNormalizedAndNullMembersAreRejected() {
        ServerInstance instance = readyInstance(true);

        instance.setWhitelistedPlayers(null);

        assertTrue(instance.getWhitelistedPlayers().isEmpty());
        assertThrows(IllegalArgumentException.class,
                () -> instance.setWhitelistedPlayers(java.util.Arrays.asList(UUID.randomUUID(), null)));
    }

    private static ServerInstance readyInstance(boolean whitelisted) {
        ServerInstance instance = new ServerInstance(
                UUID.randomUUID().toString(), "lobby", "Lobby", 25_600, whitelisted);
        instance.setServerType("LOBBY");
        instance.setMaxPlayers(10);
        instance.setStatus(ServerInstance.Status.GAME_WAITING);
        return instance;
    }
}
