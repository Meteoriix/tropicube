package fr.tropicube.core.network;

import fr.tropicube.core.TropicubeCore;
import fr.tropicube.core.managers.DatabaseManager;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Atomically exposes each contextual hint once while limiting the network to one hint per login. */
public final class ContextualHelpService {
    private static final int SESSION_TTL_SECONDS = 12 * 60 * 60;
    private final TropicubeCore core;
    private final DatabaseManager database;
    private final PlayerPreferenceService preferences;

    public ContextualHelpService(TropicubeCore core, DatabaseManager database,
                                 PlayerPreferenceService preferences) {
        this.core = core;
        this.database = database;
        this.preferences = preferences;
    }

    public CompletableFuture<Boolean> claim(UUID playerId, String hintId) {
        if (hintId == null || !hintId.matches("[A-Z0-9_]{2,64}")) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Identifiant d'aide invalide"));
        }
        return preferences.load(playerId).thenCompose(value -> {
            if (!value.contextualHelp()) return CompletableFuture.completedFuture(false);
            return database.supplyAsync(() -> {
                int inserted = database.executeUpdate("""
                        INSERT IGNORE INTO tropicube_contextual_hints(player_uuid, hint_id, shown_at)
                        VALUES (?, ?, ?)
                        """, playerId.toString(), hintId, System.currentTimeMillis());
                if (inserted != 1) return false;
                boolean sessionAvailable = core.getRedisManager().setIfAbsent(
                        "contextual-hint-session:" + playerId, hintId, SESSION_TTL_SECONDS);
                if (!sessionAvailable) {
                    database.executeUpdate("DELETE FROM tropicube_contextual_hints WHERE player_uuid = ? AND hint_id = ?",
                            playerId.toString(), hintId);
                }
                return sessionAvailable;
            });
        });
    }

    public CompletableFuture<Void> reset(UUID playerId) {
        return database.supplyAsync(() -> {
            database.executeUpdate("DELETE FROM tropicube_contextual_hints WHERE player_uuid = ?", playerId.toString());
            core.getRedisManager().delete("contextual-hint-session:" + playerId);
            return null;
        });
    }
}
