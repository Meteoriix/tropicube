package fr.tropicube.sheepwars.player;

import fr.tropicube.core.managers.DatabaseManager;
import fr.tropicube.sheepwars.TropicubeSheepwars;
import org.bukkit.entity.Player;

import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

/** Charge et sauvegarde les statistiques SheepWars persistées dans MySQL. */
public class PlayerDataManager {

    public record SheepwarsPlayerProfile(UUID uuid, String username, PlayerKit kit) {}

    private final TropicubeSheepwars plugin;
    private final DatabaseManager db;
    private final Map<UUID, SheepwarsPlayerProfile> profileCache = new ConcurrentHashMap<>();
    private final Map<UUID, Long> loadTokens = new ConcurrentHashMap<>();
    private final AtomicLong tokenSequence = new AtomicLong();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public PlayerDataManager(TropicubeSheepwars plugin, DatabaseManager db) {
        this.plugin = plugin;
        this.db = db;
    }

    public CompletableFuture<SheepwarsPlayerProfile> loadPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        String username = player.getName();
        long token = tokenSequence.incrementAndGet();
        loadTokens.put(uuid, token);
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = db.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "SELECT playerKit FROM tropicube_sheepwars WHERE uuid = ?")) {
                stmt.setString(1, uuid.toString());
                ResultSet rs = stmt.executeQuery();

                PlayerKit kit = PlayerKit.NONE;
                if (rs.next()) {
                    try {
                        kit = PlayerKit.valueOf(rs.getString("playerKit"));
                    } catch (IllegalArgumentException | NullPointerException e) {
                        plugin.getLogger().warning("[SW] Kit inconnu en base pour " + username + ", utilisation de NONE");
                    }
                } else {
                    plugin.getLogger().info("[SW] Nouveau joueur : " + username);
                }

                SheepwarsPlayerProfile profile = new SheepwarsPlayerProfile(uuid, username, kit);
                cacheIfCurrent(uuid, token, profile);
                return profile;
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "[SW] Erreur chargement joueur " + username, e);
                SheepwarsPlayerProfile fallback = new SheepwarsPlayerProfile(uuid, username, PlayerKit.NONE);
                cacheIfCurrent(uuid, token, fallback);
                return fallback;
            }
        }, executor);
    }

    private void cacheIfCurrent(UUID uuid, long token, SheepwarsPlayerProfile profile) {
        if (loadTokens.getOrDefault(uuid, -1L) == token) profileCache.put(uuid, profile);
    }

    public void unloadPlayer(UUID uuid) {
        loadTokens.remove(uuid);
        SheepwarsPlayerProfile profile = profileCache.remove(uuid);
        if (profile != null) saveKit(profile);
    }

    /** Updates the in-memory kit and persists to the database. */
    public void updateKit(UUID uuid, PlayerKit kit) {
        SheepwarsPlayerProfile current = profileCache.get(uuid);
        if (current == null) return;
        SheepwarsPlayerProfile updated = new SheepwarsPlayerProfile(current.uuid(), current.username(), kit);
        profileCache.put(uuid, updated);
        saveKit(updated);
    }

    /** Persists a profile to the database asynchronously (upsert). */
    public void saveKit(SheepwarsPlayerProfile profile) {
        CompletableFuture.runAsync(() -> persistKit(profile), executor)
                .exceptionally(error -> {
                    plugin.getLogger().log(Level.SEVERE,
                            "[SW] Erreur sauvegarde joueur " + profile.username(), error);
                    return null;
                });
    }

    private void persistKit(SheepwarsPlayerProfile profile) {
        db.executeUpdate(
            "INSERT INTO tropicube_sheepwars (uuid, username, playerKit) VALUES (?, ?, ?) " +
            "ON DUPLICATE KEY UPDATE username = VALUES(username), playerKit = VALUES(playerKit)",
            profile.uuid().toString(), profile.username(), profile.kit().name()
        );
    }

    /** Returns the cached kit for the player, or NONE if not loaded. */
    public PlayerKit getKit(UUID uuid) {
        SheepwarsPlayerProfile profile = profileCache.get(uuid);
        return profile != null ? profile.kit() : PlayerKit.NONE;
    }

    public void close() {
        loadTokens.clear();
        profileCache.values().forEach(profile -> executor.execute(() -> persistKit(profile)));
        profileCache.clear();
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                plugin.getLogger().warning("[SW] Certaines sauvegardes joueur n'ont pas terminé avant l'arrêt.");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }
}
