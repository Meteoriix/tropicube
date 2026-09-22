package fr.tropicube.lobby.gui;

import fr.tropicube.core.network.PlayerPreferenceService;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

/** Combines the independent Redis and MySQL snapshots required by the settings menu. */
final class SettingsLoadCoordinator {
    static final Duration LOAD_TIMEOUT = Duration.ofSeconds(5);

    private SettingsLoadCoordinator() { }

    static CompletableFuture<Snapshot> combine(CompletableFuture<Integer> autoReplay,
                                               CompletableFuture<PlayerPreferenceService.Preferences> preferences) {
        return combine(autoReplay, preferences, LOAD_TIMEOUT);
    }

    static CompletableFuture<Snapshot> combine(CompletableFuture<Integer> autoReplay,
                                               CompletableFuture<PlayerPreferenceService.Preferences> preferences,
                                               Duration timeout) {
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("Settings load timeout must be positive");
        }
        CompletableFuture<Integer> boundedReplay = bounded(autoReplay, "Redis", timeout);
        CompletableFuture<PlayerPreferenceService.Preferences> boundedPreferences =
                bounded(preferences, "MySQL", timeout);
        return boundedReplay.thenCombine(boundedPreferences, Snapshot::new);
    }

    private static <T> CompletableFuture<T> bounded(CompletableFuture<T> source, String name, Duration timeout) {
        return Objects.requireNonNull(source, "source")
                .orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
                .handle((value, failure) -> {
                    if (failure == null) return value;
                    Throwable cause = failure instanceof CompletionException completion && completion.getCause() != null
                            ? completion.getCause() : failure;
                    throw new CompletionException(new SettingsLoadException(name, cause));
                });
    }

    record Snapshot(int autoReplay, PlayerPreferenceService.Preferences preferences) { }

    static final class SettingsLoadException extends RuntimeException {
        SettingsLoadException(String source, Throwable cause) {
            super("Settings source unavailable: " + source, cause);
        }
    }
}
