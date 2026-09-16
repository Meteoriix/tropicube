package fr.tropicube.core.managers;

import fr.tropicube.core.util.MessageStyle;
import fr.tropicube.core.TropicubeCore;
import fr.tropicube.docker.client.RedisManager;

import java.math.BigDecimal;
import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Manager of the Tropicube economic system.
 * Uses a Redis cache for real-time scales
 * and MySQL for persistence.
 */
public class EconomyManager {

    public enum TransactionType {
        PAYMENT, REWARD, PURCHASE, ADMIN_SET, ADMIN_ADD, ADMIN_REMOVE, TRANSFER
    }

    private final TropicubeCore plugin;
    private final DatabaseManager db;
    private final RedisManager redis;

    // Local cache of scales (avoids repeated Redis requests)
    private final Map<UUID, Double> balanceCache = new ConcurrentHashMap<>();
    private volatile String currencyName;
    private volatile String currencySymbol;
    private volatile double startingBalance;

    public EconomyManager(TropicubeCore plugin, DatabaseManager db, RedisManager redis) {
        this.plugin = plugin;
        this.db = db;
        this.redis = redis;
        reloadConfiguration();
        redis.subscribeToPlayerEvents(message -> {
            if (!message.startsWith("ECONOMY_INVALIDATE:")) return;
            try {
                balanceCache.remove(UUID.fromString(message.substring("ECONOMY_INVALIDATE:".length())));
            } catch (IllegalArgumentException ignored) {
                // Ignores malformed events.
            }
        });
    }

    public void reloadConfiguration() {
        String newCurrencyName = plugin.getConfig().getString("economy.currency-name", "Coins");
        String newCurrencySymbol = plugin.getConfig().getString("economy.currency-symbol", "⚙");
        double newStartingBalance = plugin.getConfig().getDouble("economy.starting-balance", 100.0);
        if (!Double.isFinite(newStartingBalance) || newStartingBalance < 0
                || EconomyAmount.money(newStartingBalance).compareTo(EconomyAmount.MAXIMUM_BALANCE) > 0) {
            throw new IllegalArgumentException(
                    "economy.starting-balance doit être compris entre 0 et 100 milliards");
        }
        currencyName = newCurrencyName;
        currencySymbol = newCurrencySymbol;
        startingBalance = newStartingBalance;
    }

    // ===== Balance =====

    public double getBalance(UUID uuid) {
        // 1. Cache local
        Double localBalance = balanceCache.get(uuid);
        if (localBalance != null) return localBalance;

        // 2. Redis
        String cached = null;
        try {
            cached = redis.get("economy:balance:" + uuid);
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, MessageStyle.log("tc", "ECONOMY", "<yellow>Redis indisponible, repli MySQL"), e);
        }
        if (cached != null) {
            try {
                double val = Double.parseDouble(cached);
                if (Double.isFinite(val) && val >= 0
                        && val <= EconomyAmount.MAXIMUM_BALANCE.doubleValue()) {
                    balanceCache.put(uuid, val);
                    return val;
                }
            } catch (NumberFormatException ignored) {
                // A corrupted Redis value is ignored in favor of MySQL.
            }
            try {
                redis.delete("economy:balance:" + uuid);
            } catch (RuntimeException ignored) {}
        }

        // 3. Database
        return getBalanceFromDB(uuid);
    }

    private double getBalanceFromDB(UUID uuid) {
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT balance FROM tropicube_economy WHERE uuid = ?")) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                double balance = rs.getDouble("balance");
                cacheBalance(uuid, balance);
                return balance;
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, MessageStyle.log("tc", "ECONOMY", "<red>Erreur lecture balance " + uuid), e);
        }
        return 0.0;
    }

    public CompletableFuture<Double> getBalanceAsync(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> getBalance(uuid));
    }

    // ===== Operations =====

    public boolean deposit(UUID uuid, double amount, String reason) {
        if (!isValidPositiveAmount(amount)) return false;
        BigDecimal delta = EconomyAmount.money(amount);
        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);
            try {
                BigDecimal current = lockBalance(conn, uuid);
                if (current == null) return rollbackAndReturn(conn, false);
                BigDecimal updated = current.add(delta);
                if (updated.compareTo(EconomyAmount.MAXIMUM_BALANCE) > 0) {
                    return rollbackAndReturn(conn, false);
                }
                updateBalance(conn, uuid, updated, delta, BigDecimal.ZERO);
                insertTransaction(conn, null, uuid, delta, reason, TransactionType.ADMIN_ADD);
                conn.commit();
                publishBalance(uuid, updated.doubleValue());
                return true;
            } catch (SQLException | RuntimeException e) {
                rollback(conn, e);
                throw e;
            }
        } catch (SQLException e) {
            throw new DatabaseManager.DatabaseOperationException("Échec du dépôt", e);
        }
    }

    public boolean withdraw(UUID uuid, double amount, String reason) {
        if (!isValidPositiveAmount(amount)) return false;
        BigDecimal delta = EconomyAmount.money(amount);
        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);
            try {
                BigDecimal current = lockBalance(conn, uuid);
                if (current == null || current.compareTo(delta) < 0) return rollbackAndReturn(conn, false);
                BigDecimal updated = current.subtract(delta);
                updateBalance(conn, uuid, updated, BigDecimal.ZERO, delta);
                insertTransaction(conn, uuid, null, delta, reason, TransactionType.ADMIN_REMOVE);
                conn.commit();
                publishBalance(uuid, updated.doubleValue());
                return true;
            } catch (SQLException | RuntimeException e) {
                rollback(conn, e);
                throw e;
            }
        } catch (SQLException e) {
            throw new DatabaseManager.DatabaseOperationException("Échec du retrait", e);
        }
    }

    public TransferResult transfer(UUID from, UUID to, double amount) {
        if (!isValidPositiveAmount(amount)) return TransferResult.INVALID_AMOUNT;
        if (from.equals(to)) return TransferResult.SAME_PLAYER;

        double minTransfer = plugin.getConfig().getDouble("economy.min-transfer", 1.0);
        double maxTransfer = plugin.getConfig().getDouble("economy.max-transfer", 1000000.0);

        if (amount < minTransfer) return TransferResult.TOO_LOW;
        if (amount > maxTransfer) return TransferResult.TOO_HIGH;
        BigDecimal delta = EconomyAmount.money(amount);
        UUID first = from.toString().compareTo(to.toString()) < 0 ? from : to;
        UUID second = first.equals(from) ? to : from;
        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);
            try {
                BigDecimal firstBalance = lockBalance(conn, first);
                BigDecimal secondBalance = lockBalance(conn, second);
                if (firstBalance == null || secondBalance == null) return rollbackAndReturn(conn, TransferResult.ACCOUNT_NOT_FOUND);
                BigDecimal fromBalance = first.equals(from) ? firstBalance : secondBalance;
                BigDecimal toBalance = first.equals(to) ? firstBalance : secondBalance;
                if (fromBalance.compareTo(delta) < 0) return rollbackAndReturn(conn, TransferResult.INSUFFICIENT_FUNDS);
                if (toBalance.add(delta).compareTo(EconomyAmount.MAXIMUM_BALANCE) > 0) {
                    return rollbackAndReturn(conn, TransferResult.RECIPIENT_BALANCE_LIMIT);
                }

                BigDecimal updatedFrom = fromBalance.subtract(delta);
                BigDecimal updatedTo = toBalance.add(delta);
                updateBalance(conn, from, updatedFrom, BigDecimal.ZERO, delta);
                updateBalance(conn, to, updatedTo, delta, BigDecimal.ZERO);
                insertTransaction(conn, from, to, delta, "Paiement joueur", TransactionType.TRANSFER);
                conn.commit();
                publishBalance(from, updatedFrom.doubleValue());
                publishBalance(to, updatedTo.doubleValue());
                return TransferResult.SUCCESS;
            } catch (SQLException | RuntimeException e) {
                rollback(conn, e);
                throw e;
            }
        } catch (SQLException e) {
            throw new DatabaseManager.DatabaseOperationException("Échec du transfert", e);
        }
    }

    public void setBalance(UUID uuid, double amount) {
        if (!Double.isFinite(amount)) {
            throw new IllegalArgumentException("Le solde doit être un nombre fini");
        }
        double newBalance = EconomyAmount.clampBalance(EconomyAmount.money(amount)).doubleValue();
        db.executeUpdate(
                "INSERT INTO tropicube_economy (uuid, balance, last_updated) VALUES (?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE balance = ?, last_updated = ?",
                uuid.toString(), newBalance, System.currentTimeMillis(),
                newBalance, System.currentTimeMillis()
        );
        publishBalance(uuid, newBalance);
    }

    public void createAccount(UUID uuid) {
        int inserted = db.executeUpdate(
                "INSERT IGNORE INTO tropicube_economy (uuid, balance, last_updated) VALUES (?, ?, ?)",
                uuid.toString(), EconomyAmount.clampBalance(EconomyAmount.money(startingBalance)), System.currentTimeMillis());
        invalidateCache(uuid);
        if (inserted > 0) {
            plugin.getLogger().info(MessageStyle.log("tc", "ECONOMY", "<gray>Compte créé pour " + uuid + " avec " + startingBalance + " " + currencyName));
        }
    }

    private void cacheBalance(UUID uuid, double balance) {
        balanceCache.put(uuid, balance);
        try {
            redis.set("economy:balance:" + uuid, String.valueOf(balance), 3600);
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, MessageStyle.log("tc", "ECONOMY", "<yellow>Impossible de mettre le solde en cache Redis"), e);
        }
    }

    private void publishBalance(UUID uuid, double balance) {
        cacheBalance(uuid, balance);
        try {
            redis.publishPlayerEvent("ECONOMY_INVALIDATE", uuid.toString());
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, MessageStyle.log("tc", "ECONOMY", "<yellow>Synchronisation inter-serveurs impossible"), e);
        }
    }

    private boolean isValidPositiveAmount(double amount) { return Double.isFinite(amount) && amount > 0; }

    private BigDecimal lockBalance(Connection conn, UUID uuid) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT balance FROM tropicube_economy WHERE uuid = ? FOR UPDATE")) {
            stmt.setString(1, uuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getBigDecimal(1) : null;
            }
        }
    }

    private void updateBalance(Connection conn, UUID uuid, BigDecimal balance,
                               BigDecimal earned, BigDecimal spent) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(
                "UPDATE tropicube_economy SET balance = ?, total_earned = total_earned + ?, " +
                "total_spent = total_spent + ?, last_updated = ? WHERE uuid = ?")) {
            stmt.setBigDecimal(1, balance);
            stmt.setBigDecimal(2, earned);
            stmt.setBigDecimal(3, spent);
            stmt.setLong(4, System.currentTimeMillis());
            stmt.setString(5, uuid.toString());
            if (stmt.executeUpdate() != 1) throw new SQLException("Compte économique introuvable: " + uuid);
        }
    }

    private void insertTransaction(Connection conn, UUID from, UUID to, BigDecimal amount,
                                   String reason, TransactionType type) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO tropicube_transactions (from_uuid, to_uuid, amount, reason, transaction_type, timestamp) " +
                "VALUES (?, ?, ?, ?, ?, ?)")) {
            stmt.setString(1, from == null ? null : from.toString());
            stmt.setString(2, to == null ? null : to.toString());
            stmt.setBigDecimal(3, amount);
            stmt.setString(4, reason);
            stmt.setString(5, type.name());
            stmt.setLong(6, System.currentTimeMillis());
            stmt.executeUpdate();
        }
    }

    private void rollback(Connection conn, Throwable failure) {
        try {
            conn.rollback();
        } catch (SQLException rollbackFailure) {
            failure.addSuppressed(rollbackFailure);
        }
    }

    private <T> T rollbackAndReturn(Connection conn, T result) throws SQLException {
        conn.rollback();
        return result;
    }

    // ===== Classement =====

    public List<Map.Entry<String, Double>> getTopBalances(int limit) {
        List<Map.Entry<String, Double>> top = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT p.username, e.balance FROM tropicube_economy e " +
                     "JOIN tropicube_players p ON e.uuid = p.uuid " +
                     "ORDER BY e.balance DESC LIMIT ?")) {
            stmt.setInt(1, limit);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                top.add(Map.entry(rs.getString("username"), rs.getDouble("balance")));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, MessageStyle.log("tc", "ECONOMY", "<yellow>Erreur top balances"), e);
        }
        return top;
    }

    public void invalidateCache(UUID uuid) {
        balanceCache.remove(uuid);
        try {
            redis.delete("economy:balance:" + uuid);
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.FINE, MessageStyle.log("tc", "ECONOMY", "<gray>Cache Redis déjà indisponible"), e);
        }
    }

    // ===== Formatage =====

    public String format(double amount) {
        return EconomyAmount.format(amount, currencySymbol);
    }

    public enum TransferResult {
        SUCCESS, INSUFFICIENT_FUNDS, RECIPIENT_BALANCE_LIMIT, SAME_PLAYER, INVALID_AMOUNT, TOO_LOW, TOO_HIGH,
        ACCOUNT_NOT_FOUND;

        public String getMessage() {
            return switch (this) {
                case SUCCESS -> "<green>Transfert effectué avec succès.";
                case INSUFFICIENT_FUNDS -> "<red>Fonds insuffisants.";
                case RECIPIENT_BALANCE_LIMIT -> "<red>Le destinataire a atteint la limite de solde.";
                case SAME_PLAYER -> "<red>Vous ne pouvez pas vous envoyer de l'argent.";
                case INVALID_AMOUNT -> "<red>Montant invalide (doit être positif).";
                case TOO_LOW -> "<red>Montant trop faible.";
                case TOO_HIGH -> "<red>Montant trop élevé.";
                case ACCOUNT_NOT_FOUND -> "<red>Compte économique introuvable.";
            };
        }
    }
}
