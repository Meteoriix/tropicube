package fr.tropicube.lobby.gui;

import fr.tropicube.core.network.PlayerPreferenceService;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SettingsLoadCoordinatorTest {
    private static final PlayerPreferenceService.Preferences PREFERENCES =
            PlayerPreferenceService.Preferences.defaults();

    @Test
    void combinesRedisAndMySqlSnapshots() throws Exception {
        SettingsLoadCoordinator.Snapshot snapshot = SettingsLoadCoordinator.combine(
                CompletableFuture.completedFuture(4), CompletableFuture.completedFuture(PREFERENCES))
                .get(1, TimeUnit.SECONDS);

        assertEquals(4, snapshot.autoReplay());
        assertSame(PREFERENCES, snapshot.preferences());
    }

    @Test
    void identifiesRedisFailure() {
        ExecutionException error = assertThrows(ExecutionException.class, () -> SettingsLoadCoordinator.combine(
                CompletableFuture.failedFuture(new IllegalStateException("redis down")),
                CompletableFuture.completedFuture(PREFERENCES)).get());

        SettingsLoadCoordinator.SettingsLoadException failure = assertInstanceOf(
                SettingsLoadCoordinator.SettingsLoadException.class, error.getCause());
        assertEquals("Settings source unavailable: Redis", failure.getMessage());
    }

    @Test
    void identifiesMySqlFailure() {
        ExecutionException error = assertThrows(ExecutionException.class, () -> SettingsLoadCoordinator.combine(
                CompletableFuture.completedFuture(-1),
                CompletableFuture.failedFuture(new IllegalStateException("mysql down"))).get());

        SettingsLoadCoordinator.SettingsLoadException failure = assertInstanceOf(
                SettingsLoadCoordinator.SettingsLoadException.class, error.getCause());
        assertEquals("Settings source unavailable: MySQL", failure.getMessage());
    }

    @Test
    void failsInsteadOfWaitingForeverForRedis() {
        ExecutionException error = assertThrows(ExecutionException.class, () -> SettingsLoadCoordinator.combine(
                new CompletableFuture<>(), CompletableFuture.completedFuture(PREFERENCES), Duration.ofMillis(50))
                .get(1, TimeUnit.SECONDS));

        SettingsLoadCoordinator.SettingsLoadException failure = assertInstanceOf(
                SettingsLoadCoordinator.SettingsLoadException.class, error.getCause());
        assertEquals("Settings source unavailable: Redis", failure.getMessage());
    }

    @Test
    void failsInsteadOfWaitingForeverForMySql() {
        ExecutionException error = assertThrows(ExecutionException.class, () -> SettingsLoadCoordinator.combine(
                CompletableFuture.completedFuture(-1), new CompletableFuture<>(), Duration.ofMillis(50))
                .get(1, TimeUnit.SECONDS));

        SettingsLoadCoordinator.SettingsLoadException failure = assertInstanceOf(
                SettingsLoadCoordinator.SettingsLoadException.class, error.getCause());
        assertEquals("Settings source unavailable: MySQL", failure.getMessage());
    }
}
