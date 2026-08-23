package fr.tropicube.core.network;

import fr.tropicube.core.TropicubeCore;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Atomically debits currency and promotes a player to a configured grade. */
public final class GradePurchaseService {
    public enum Result { PURCHASED, ALREADY_OWNED, INSUFFICIENT_FUNDS, STALE_GRADE, INVALID }

    private final TropicubeCore core;

    public GradePurchaseService(TropicubeCore core) {
        this.core = Objects.requireNonNull(core, "core");
    }

    public CompletableFuture<Result> purchase(UUID playerId, String expectedGrade, String targetGrade, int price) {
        if (price < 0 || expectedGrade == null || targetGrade == null) {
            return CompletableFuture.completedFuture(Result.INVALID);
        }
        String expected = expectedGrade.toUpperCase(Locale.ROOT);
        String target = targetGrade.toUpperCase(Locale.ROOT);
        var targetInfo = core.getPermissionManager().getAllGrades().get(target);
        if (targetInfo == null) return CompletableFuture.completedFuture(Result.INVALID);
        return core.getDatabaseManager().supplyAsync(() -> transact(playerId, expected, target, price))
                .thenApply(result -> {
                    if (result == Result.PURCHASED) {
                        core.getEconomyManager().invalidateCache(playerId);
                        core.getPermissionManager().applyCommittedGrade(playerId, target);
                    }
                    return result;
                });
    }

    private Result transact(UUID playerId, String expected, String target, int price) throws SQLException {
        try (Connection connection = core.getDatabaseManager().getConnection()) {
            connection.setAutoCommit(false);
            try {
                String current = lockGrade(connection, playerId);
                if (current == null) return rollback(connection, Result.INVALID);
                if (!current.equalsIgnoreCase(expected)) return rollback(connection, Result.STALE_GRADE);
                var currentInfo = core.getPermissionManager().getAllGrades().get(current.toUpperCase(Locale.ROOT));
                var targetInfo = core.getPermissionManager().getAllGrades().get(target);
                if (currentInfo == null || targetInfo == null) return rollback(connection, Result.INVALID);
                if (currentInfo.priority() >= targetInfo.priority()) return rollback(connection, Result.ALREADY_OWNED);

                BigDecimal charge = BigDecimal.valueOf(price).setScale(2);
                BigDecimal balance = lockBalance(connection, playerId);
                if (balance == null || balance.compareTo(charge) < 0) {
                    return rollback(connection, Result.INSUFFICIENT_FUNDS);
                }
                try (PreparedStatement update = connection.prepareStatement("""
                        UPDATE tropicube_economy SET balance=?, total_spent=total_spent+?, last_updated=? WHERE uuid=?
                        """)) {
                    update.setBigDecimal(1, balance.subtract(charge));
                    update.setBigDecimal(2, charge);
                    update.setLong(3, System.currentTimeMillis());
                    update.setString(4, playerId.toString());
                    if (update.executeUpdate() != 1) throw new SQLException("Compte économique introuvable");
                }
                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE tropicube_players SET grade=?, grade_expiry=-1 WHERE uuid=?")) {
                    update.setString(1, target);
                    update.setString(2, playerId.toString());
                    if (update.executeUpdate() != 1) throw new SQLException("Joueur introuvable");
                }
                if (price > 0) insertTransaction(connection, playerId, charge, target);
                connection.commit();
                return Result.PURCHASED;
            } catch (SQLException | RuntimeException failure) {
                connection.rollback();
                throw failure;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    private static String lockGrade(Connection connection, UUID playerId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT grade FROM tropicube_players WHERE uuid=? FOR UPDATE")) {
            statement.setString(1, playerId.toString());
            try (ResultSet result = statement.executeQuery()) { return result.next() ? result.getString(1) : null; }
        }
    }

    private static BigDecimal lockBalance(Connection connection, UUID playerId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT balance FROM tropicube_economy WHERE uuid=? FOR UPDATE")) {
            statement.setString(1, playerId.toString());
            try (ResultSet result = statement.executeQuery()) { return result.next() ? result.getBigDecimal(1) : null; }
        }
    }

    private static void insertTransaction(Connection connection, UUID playerId, BigDecimal charge, String target)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO tropicube_transactions(from_uuid,to_uuid,amount,reason,transaction_type,timestamp)
                VALUES (?,NULL,?,?,?,?)
                """)) {
            statement.setString(1, playerId.toString());
            statement.setBigDecimal(2, charge);
            statement.setString(3, "Achat grade " + target);
            statement.setString(4, "PURCHASE");
            statement.setLong(5, System.currentTimeMillis());
            statement.executeUpdate();
        }
    }

    private static Result rollback(Connection connection, Result result) throws SQLException {
        connection.rollback();
        return result;
    }
}
