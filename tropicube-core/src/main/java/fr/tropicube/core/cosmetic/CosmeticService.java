package fr.tropicube.core.cosmetic;

import fr.tropicube.core.managers.DatabaseManager;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** SQL authority for ownership and equipment; every mutation locks the existing player row first. */
public final class CosmeticService {
    /** Business outcomes; infrastructure failures complete the future exceptionally. */
    public enum Result { SUCCESS, OWNED, LOCKED, INSUFFICIENT_FUNDS, ACCOUNT_NOT_FOUND, PRICE_CHANGED, INVALID_SELECTION }
    /** Catalogue views use the same access rule as equipment and rendering. */
    public enum Filter { ALL, AVAILABLE, LOCKED }
    /** A coherent snapshot read on one connection; unknown removed catalogue entries remain stored. */
    public record Snapshot(int level, int vipLevel, long experience, BigDecimal balance,
                           Set<String> purchased, Map<CosmeticCatalog.Category, String> equipped) {
        public Snapshot { purchased = Set.copyOf(purchased); equipped = Map.copyOf(equipped); }
        public List<CosmeticCatalog.Entry> entries(CosmeticCatalog catalog, CosmeticCatalog.Category category, Filter filter) {
            return catalog.entries().stream().filter(entry -> entry.category() == category)
                    .filter(entry -> filter == Filter.ALL || available(entry) == (filter == Filter.AVAILABLE)).toList();
        }
        public boolean available(CosmeticCatalog.Entry entry) {
            return entry.available(level, vipLevel, purchased.contains(entry.id()));
        }
    }
    private final DatabaseManager database;
    private final CosmeticCatalog catalog;
    private final java.util.function.Consumer<UUID> invalidateBalance;
    public CosmeticService(DatabaseManager database, CosmeticCatalog catalog, java.util.function.Consumer<UUID> invalidateBalance) {
        this.database = database;
        this.catalog = catalog;
        this.invalidateBalance = invalidateBalance;
    }
    public CosmeticCatalog catalog() { return catalog; }
    public CompletableFuture<Snapshot> snapshot(UUID player) {
        return database.supplyAsync(() -> {
            try (Connection connection = database.getConnection()) { return consult(connection, player); }
        });
    }
    /** Purchases never equip implicitly. The confirmed price is checked under the account lock. */
    public CompletableFuture<Result> buy(UUID player, String id, int expectedPrice) {
        CosmeticCatalog.Entry entry;
        try { entry = catalog.find(id); }
        catch (IllegalArgumentException invalid) { return CompletableFuture.completedFuture(Result.INVALID_SELECTION); }
        return database.supplyAsync(() -> {
            Result result;
            try (Connection connection = database.getConnection()) { result = purchase(connection, player, entry, expectedPrice); }
            if (result == Result.SUCCESS) invalidateBalance.accept(player);
            return result;
        });
    }
    /** Synchronous transaction boundary; requires a dedicated worker connection, never the Paper thread. */
    public static Result purchase(Connection connection, UUID player, CosmeticCatalog.Entry entry, int expectedPrice) throws SQLException {
        requireAutocommit(connection);
        connection.setAutoCommit(false);
        try {
            if (!lockPlayer(connection, player)) { connection.rollback(); return Result.ACCOUNT_NOT_FOUND; }
            Snapshot snapshot = read(connection, player);
            Result result;
            if (snapshot.purchased().contains(entry.id())) result = Result.OWNED;
            else if (entry.access() != CosmeticCatalog.Access.CURRENCY) result = Result.LOCKED;
            else if (entry.requirement() != expectedPrice) result = Result.PRICE_CHANGED;
            else {
                BigDecimal balance;
                try (var statement = connection.prepareStatement("SELECT balance FROM tropicube_economy WHERE uuid=? FOR UPDATE")) {
                    statement.setString(1, player.toString());
                    try (var rows = statement.executeQuery()) {
                        balance = rows.next() ? rows.getBigDecimal(1) : null;
                    }
                }
                var price = BigDecimal.valueOf(entry.requirement());
                if (balance == null) result = Result.ACCOUNT_NOT_FOUND;
                else if (balance.compareTo(price) < 0) result = Result.INSUFFICIENT_FUNDS;
                else {
                    execute(connection, "UPDATE tropicube_economy SET balance=balance-?,total_spent=total_spent+?,last_updated=? WHERE uuid=?",
                            price, price, System.currentTimeMillis(), player.toString());
                    execute(connection, "INSERT INTO tropicube_cosmetic_purchases(player_uuid,cosmetic_id,price,acquired_at) VALUES(?,?,?,?)",
                            player.toString(), entry.id(), price, System.currentTimeMillis());
                    execute(connection, "INSERT INTO tropicube_transactions(from_uuid,to_uuid,amount,reason,transaction_type,timestamp) VALUES(?,NULL,?,?,?,?)",
                            player.toString(), price, "Cosmetic: " + entry.id(), "PURCHASE", System.currentTimeMillis());
                    result = Result.SUCCESS;
                }
            }
            connection.commit();
            return result;
        } catch (SQLException | RuntimeException error) {
            connection.rollback(); throw error;
        } finally { connection.setAutoCommit(true); }
    }
    /** Null id unequips; invalid identifiers never schedule a mutation. */
    public CompletableFuture<Result> equip(UUID player, CosmeticCatalog.Category category, String id) {
        CosmeticCatalog.Entry entry;
        try { entry = id == null ? null : catalog.find(id); }
        catch (IllegalArgumentException invalid) { return CompletableFuture.completedFuture(Result.INVALID_SELECTION); }
        if (category == null || entry != null && entry.category() != category)
            return CompletableFuture.completedFuture(Result.INVALID_SELECTION);
        return database.supplyAsync(() -> {
            try (Connection connection = database.getConnection()) { return equip(connection, player, category, entry); }
        });
    }

    /** Worker-only transaction boundary. The connection must not already own a transaction. */
    public static Result equip(Connection connection, UUID player, CosmeticCatalog.Category category,
                               CosmeticCatalog.Entry entry) throws SQLException {
        if (category == null || entry != null && entry.category() != category) return Result.INVALID_SELECTION;
        requireAutocommit(connection);
        connection.setAutoCommit(false);
        try {
            if (!lockPlayer(connection, player)) { connection.rollback(); return Result.ACCOUNT_NOT_FOUND; }
            if (entry != null && !read(connection, player).available(entry)) { connection.rollback(); return Result.LOCKED; }
            execute(connection, "DELETE FROM tropicube_cosmetic_equipment WHERE player_uuid=? AND category=?", player.toString(), category.name());
            if (entry != null) execute(connection, "INSERT INTO tropicube_cosmetic_equipment(player_uuid,category,cosmetic_id) VALUES(?,?,?)",
                    player.toString(), category.name(), entry.id());
            connection.commit();
            return Result.SUCCESS;
        } catch (SQLException | RuntimeException error) {
            connection.rollback();
            throw error;
        } finally { connection.setAutoCommit(true); }
    }

    /** Coherent worker-only snapshot across account, purchases and selections. */
    public static Snapshot consult(Connection connection, UUID player) throws SQLException {
        requireAutocommit(connection);
        int isolation = connection.getTransactionIsolation();
        connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
        connection.setAutoCommit(false);
        try {
            Snapshot snapshot = read(connection, player);
            connection.commit();
            return snapshot;
        } catch (SQLException | RuntimeException error) {
            connection.rollback();
            throw error;
        } finally {
            connection.setAutoCommit(true);
            connection.setTransactionIsolation(isolation);
        }
    }

    private static void requireAutocommit(Connection connection) throws SQLException {
        if (!connection.getAutoCommit()) throw new SQLException("Cosmetic operations require a dedicated transaction");
    }
    private static boolean lockPlayer(Connection connection, UUID player) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT uuid FROM tropicube_players WHERE uuid=? FOR UPDATE")) {
            statement.setString(1, player.toString());
            try (var rows = statement.executeQuery()) { return rows.next(); }
        }
    }
    private static Snapshot read(Connection connection, UUID player) throws SQLException {
        int level = 1, vip = 0; long experience = 0; BigDecimal balance = BigDecimal.ZERO;
        try (var statement = connection.prepareStatement("""
                SELECT p.vip_level,COALESCE(n.level,1),COALESCE(n.experience,0),COALESCE(e.balance,0)
                FROM tropicube_players p LEFT JOIN tropicube_network_progression n ON n.player_uuid=p.uuid
                LEFT JOIN tropicube_economy e ON e.uuid=p.uuid WHERE p.uuid=?
                """)) {
            statement.setString(1, player.toString());
            try (var rows = statement.executeQuery()) {
                if (!rows.next()) throw new SQLException("Cosmetic account absent: " + player);
                vip = rows.getInt(1); level = rows.getInt(2); experience = rows.getLong(3); balance = rows.getBigDecimal(4);
            }
        }
        var purchased = new HashSet<String>();
        try (var statement = connection.prepareStatement("SELECT cosmetic_id FROM tropicube_cosmetic_purchases WHERE player_uuid=?")) {
            statement.setString(1, player.toString());
            try (var rows = statement.executeQuery()) { while (rows.next()) purchased.add(rows.getString(1)); }
        }
        var equipped = new EnumMap<CosmeticCatalog.Category, String>(CosmeticCatalog.Category.class);
        try (var statement = connection.prepareStatement("SELECT category,cosmetic_id FROM tropicube_cosmetic_equipment WHERE player_uuid=?")) {
            statement.setString(1, player.toString());
            try (var rows = statement.executeQuery()) { while (rows.next()) equipped.put(CosmeticCatalog.Category.valueOf(rows.getString(1)), rows.getString(2)); }
        }
        return new Snapshot(level, vip, experience, balance, purchased, equipped);
    }
    private static void execute(Connection connection, String sql, Object... values) throws SQLException {
        try (var statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < values.length; index++) statement.setObject(index + 1, values[index]);
            statement.executeUpdate();
        }
    }
}
