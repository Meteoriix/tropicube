package fr.tropicube.core.progression;

import fr.tropicube.core.TropicubeCore;
import fr.tropicube.core.managers.DatabaseManager;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Persistent network experience with a monotonic, configurable-independent level curve. */
public final class NetworkProgressionService {
    public record Progression(long experience, int level) {}
    private final TropicubeCore plugin;
    private final DatabaseManager database;

    public NetworkProgressionService(TropicubeCore plugin, DatabaseManager database) {
        this.plugin = plugin;
        this.database = database;
    }

    public CompletableFuture<Progression> get(UUID playerId) {
        return database.supplyAsync(() -> load(playerId));
    }

    public CompletableFuture<Progression> addExperience(UUID playerId, long amount) {
        if (amount <= 0) throw new IllegalArgumentException("amount doit être strictement positif");
        return database.supplyAsync(() -> add(playerId, amount))
                .whenComplete((progression, error) -> {
                    if (error == null) {
                        display(playerId, progression);
                        contributeToGuild(playerId, amount);
                    }
                });
    }

    private void contributeToGuild(UUID playerId, long amount) {
        try {
            plugin.getGuildService().contribute(playerId, amount).exceptionally(contributionError -> {
                plugin.getLogger().warning("Impossible de contribuer l'XP de " + playerId
                        + " à sa guilde : " + contributionError.getMessage());
                return 0L;
            });
        } catch (RuntimeException contributionError) {
            plugin.getLogger().warning("Impossible de planifier la contribution d'XP de " + playerId
                    + " : " + contributionError.getMessage());
        }
    }

    /** Loads and displays the persistent network level without blocking the Paper thread. */
    public void refreshDisplay(UUID playerId) {
        get(playerId).thenAccept(progression -> display(playerId, progression));
    }

    private void display(UUID playerId, Progression progression) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player == null || !player.isOnline()) return;
            player.setLevel(progression.level());
            player.setExp(progressWithinLevel(progression.experience()));
        });
    }

    private Progression load(UUID playerId) throws SQLException {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT experience, level FROM tropicube_network_progression WHERE player_uuid = ?")) {
            statement.setString(1, playerId.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? new Progression(result.getLong(1), result.getInt(2)) : new Progression(0, 1);
            }
        }
    }

    private Progression add(UUID playerId, long amount) throws SQLException {
        try (Connection connection = database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                long experience = 0;
                try (PreparedStatement select = connection.prepareStatement(
                        "SELECT experience FROM tropicube_network_progression WHERE player_uuid = ? FOR UPDATE")) {
                    select.setString(1, playerId.toString());
                    try (ResultSet result = select.executeQuery()) { if (result.next()) experience = result.getLong(1); }
                }
                long updated = Math.addExact(experience, amount);
                int level = levelForExperience(updated);
                try (PreparedStatement upsert = connection.prepareStatement("""
                        INSERT INTO tropicube_network_progression(player_uuid, experience, level, updated_at)
                        VALUES (?, ?, ?, ?)
                        ON DUPLICATE KEY UPDATE experience=VALUES(experience), level=VALUES(level), updated_at=VALUES(updated_at)
                        """)) {
                    upsert.setString(1, playerId.toString());
                    upsert.setLong(2, updated);
                    upsert.setInt(3, level);
                    upsert.setLong(4, System.currentTimeMillis());
                    upsert.executeUpdate();
                }
                connection.commit();
                return new Progression(updated, level);
            } catch (SQLException | RuntimeException error) {
                connection.rollback();
                throw error;
            } finally { connection.setAutoCommit(true); }
        }
    }

    /** Total XP follows 100 × (level-1)^2, making every threshold explicit and stable. */
    public static int levelForExperience(long experience) {
        if (experience < 0) throw new IllegalArgumentException("experience doit être positive");
        long root = (long) Math.sqrt(experience / 100.0);
        while (100L * (root + 1) * (root + 1) <= experience && root < 46_000_000) root++;
        while (100L * root * root > experience) root--;
        return Math.toIntExact(Math.min(Integer.MAX_VALUE - 1L, root + 1));
    }

    /** Returns the normalized progress between the current and next network-level thresholds. */
    public static float progressWithinLevel(long experience) {
        int level = levelForExperience(experience);
        long currentThreshold = thresholdForLevel(level);
        long nextThreshold = thresholdForLevel(level + 1);
        if (nextThreshold <= currentThreshold) return 0.0f;
        double progress = (experience - currentThreshold) / (double) (nextThreshold - currentThreshold);
        return (float) Math.max(0.0, Math.min(0.999_999, progress));
    }

    /** Remaining cumulative XP to the next level, using the same curve as mission rewards. */
    public static long experienceToNextLevel(long experience) {
        return Math.max(0, thresholdForLevel(levelForExperience(experience) + 1) - experience);
    }

    private static long thresholdForLevel(int level) {
        long offset = Math.max(0L, (long) level - 1L);
        try {
            return Math.multiplyExact(100L, Math.multiplyExact(offset, offset));
        } catch (ArithmeticException ignored) {
            return Long.MAX_VALUE;
        }
    }
}
