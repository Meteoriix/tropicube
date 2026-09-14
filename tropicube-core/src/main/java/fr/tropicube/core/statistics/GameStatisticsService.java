package fr.tropicube.core.statistics;

import fr.tropicube.core.managers.DatabaseManager;
import fr.tropicube.docker.client.RedisManager;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.concurrent.CompletableFuture;

/** Persists game-neutral match results atomically outside the Paper thread. */
public final class GameStatisticsService {
    private static final System.Logger LOGGER = System.getLogger(GameStatisticsService.class.getName());
    private final DatabaseManager database;
    private final RedisManager redis;
    public GameStatisticsService(DatabaseManager database, RedisManager redis) { this.database = database; this.redis = redis; }
    public CompletableFuture<Boolean> persist(NetworkGameResult result) {
        return database.supplyAsync(() -> persistTransaction(result)).thenApply(inserted -> {
            if (inserted) result.players().forEach(player -> {
                try { redis.delete("statistics:" + player.playerId()); }
                catch (RuntimeException failure) { LOGGER.log(System.Logger.Level.WARNING,
                        "Statistiques persistées mais cache Redis non invalidé pour " + player.playerId(), failure); }
            });
            return inserted;
        });
    }
    private boolean persistTransaction(NetworkGameResult result) throws SQLException {
        try (Connection connection = database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                int inserted;
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT IGNORE INTO tropicube_game_results(match_id, game_id, end_cause, winners, ended_at)
                        VALUES (?, ?, ?, ?, ?)
                        """)) {
                    statement.setString(1, result.matchId().toString()); statement.setString(2, result.gameId());
                    statement.setString(3, result.cause()); statement.setString(4, String.join(",", result.winners()));
                    statement.setTimestamp(5, Timestamp.from(result.endedAt())); inserted = statement.executeUpdate();
                }
                if (inserted == 0) { connection.rollback(); return false; }
                try (PreparedStatement detail = connection.prepareStatement("""
                        INSERT INTO tropicube_game_result_players
                        (match_id, player_uuid, winner, draw, eliminations, deaths, objectives) VALUES (?, ?, ?, ?, ?, ?, ?)
                        """); PreparedStatement aggregate = connection.prepareStatement("""
                        INSERT INTO tropicube_game_statistics
                        (player_uuid, game_id, games, wins, draws, eliminations, deaths, objectives)
                        VALUES (?, ?, 1, ?, ?, ?, ?, ?)
                        ON DUPLICATE KEY UPDATE games=games+1, wins=wins+VALUES(wins), draws=draws+VALUES(draws),
                        eliminations=eliminations+VALUES(eliminations), deaths=deaths+VALUES(deaths), objectives=objectives+VALUES(objectives)
                        """)) {
                    for (var player : result.players()) {
                        detail.setString(1, result.matchId().toString()); detail.setString(2, player.playerId().toString());
                        detail.setBoolean(3, player.winner()); detail.setBoolean(4, player.draw());
                        detail.setInt(5, player.eliminations()); detail.setInt(6, player.deaths()); detail.setInt(7, player.objectives()); detail.addBatch();
                        aggregate.setString(1, player.playerId().toString()); aggregate.setString(2, result.gameId());
                        aggregate.setInt(3, player.winner() ? 1 : 0); aggregate.setInt(4, player.draw() ? 1 : 0);
                        aggregate.setInt(5, player.eliminations()); aggregate.setInt(6, player.deaths()); aggregate.setInt(7, player.objectives()); aggregate.addBatch();
                    }
                    detail.executeBatch(); aggregate.executeBatch();
                }
                connection.commit(); return true;
            } catch (SQLException failure) { connection.rollback(); throw failure; }
            finally { connection.setAutoCommit(true); }
        }
    }
}
