package fr.tropicube.core.progression;

import fr.tropicube.core.managers.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Persistent network experience with a monotonic, configurable-independent level curve. */
public final class NetworkProgressionService {
    public record Progression(long experience, int level) {}
    private final DatabaseManager database;

    public NetworkProgressionService(DatabaseManager database) { this.database = database; }

    public CompletableFuture<Progression> get(UUID playerId) {
        return database.supplyAsync(() -> load(playerId));
    }

    public CompletableFuture<Progression> addExperience(UUID playerId, long amount) {
        if (amount <= 0) throw new IllegalArgumentException("amount doit être strictement positif");
        return database.supplyAsync(() -> add(playerId, amount));
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
}
