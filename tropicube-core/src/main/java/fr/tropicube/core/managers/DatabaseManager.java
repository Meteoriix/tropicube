package fr.tropicube.core.managers;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import fr.tropicube.core.TropicubeCore;

import java.sql.*;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Gestionnaire de base de données MySQL avec pool de connexions HikariCP.
 * Gère la création des tables et fournit des méthodes utilitaires.
 */
public class DatabaseManager {

    private final TropicubeCore plugin;

    private HikariDataSource dataSource;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public DatabaseManager(TropicubeCore plugin) {
        this.plugin = plugin;
    }

    public void initialize() throws SQLException {
        HikariConfig config = new HikariConfig();

        config.setJdbcUrl("jdbc:mysql://" +
                plugin.getConfiguredString("TROPICUBE_DB_HOST", "database.host", "localhost") + ":" +
                plugin.getConfiguredInt("TROPICUBE_DB_PORT", "database.port", 3306) + "/" +
                plugin.getConfiguredString("TROPICUBE_DB_NAME", "database.name", "tropicube") +
                "?useSSL=false&characterEncoding=UTF-8&socketTimeout=30000");
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        config.setUsername(plugin.getConfiguredString("TROPICUBE_DB_USER", "database.user", "root"));
        config.setPassword(plugin.getConfiguredString("TROPICUBE_DB_PASSWORD", "database.password", ""));
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
        config.setKeepaliveTime(60000);
        config.setPoolName("Tropicube-DB");
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        dataSource = new HikariDataSource(config);

        createTables();
    }

    private void createTables() throws SQLException {
        String[] tables = {
            // Table joueurs
            """
            CREATE TABLE IF NOT EXISTS tropicube_players (
                uuid VARCHAR(36) PRIMARY KEY,
                username VARCHAR(16) NOT NULL,
                display_name VARCHAR(64),
                first_join BIGINT NOT NULL,
                last_join BIGINT NOT NULL,
                play_time BIGINT DEFAULT 0,
                language VARCHAR(8) DEFAULT 'fr',
                grade VARCHAR(32) DEFAULT 'JOUEUR',
                grade_expiry BIGINT DEFAULT -1,
                vipLevel SMALLINT DEFAULT 0,
                staffLevel SMALLINT DEFAULT 0,
                is_banned BOOLEAN DEFAULT FALSE,
                ban_reason TEXT,
                ban_expiry BIGINT DEFAULT 0,
                INDEX idx_username (username)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,

            // Table monnaie
            """
            CREATE TABLE IF NOT EXISTS tropicube_economy (
                uuid VARCHAR(36) PRIMARY KEY,
                balance DECIMAL(19,2) DEFAULT 0.00,
                total_earned DECIMAL(19,2) DEFAULT 0.00,
                total_spent DECIMAL(19,2) DEFAULT 0.00,
                last_updated BIGINT NOT NULL,
                FOREIGN KEY (uuid) REFERENCES tropicube_players(uuid) ON DELETE CASCADE
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,

            // Table transactions
            """
            CREATE TABLE IF NOT EXISTS tropicube_transactions (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                from_uuid VARCHAR(36),
                to_uuid VARCHAR(36),
                amount DECIMAL(19,2) NOT NULL,
                reason VARCHAR(255),
                transaction_type VARCHAR(32) NOT NULL,
                timestamp BIGINT NOT NULL,
                INDEX idx_from (from_uuid),
                INDEX idx_to (to_uuid),
                INDEX idx_timestamp (timestamp)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,

            // Table sanctions (mutes, warns, kicks)
            """
            CREATE TABLE IF NOT EXISTS tropicube_sanctions (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                player_uuid VARCHAR(36) NOT NULL,
                type VARCHAR(16) NOT NULL,
                reason TEXT,
                staff_uuid VARCHAR(36),
                staff_name VARCHAR(16),
                timestamp BIGINT NOT NULL,
                expiry BIGINT DEFAULT -1,
                active BOOLEAN DEFAULT TRUE,
                INDEX idx_player (player_uuid),
                INDEX idx_type (type),
                INDEX idx_active (active)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,

            // Table grades
            """
            CREATE TABLE IF NOT EXISTS tropicube_grades (
                name VARCHAR(32) PRIMARY KEY,
                display_name VARCHAR(64) NOT NULL,
                prefix VARCHAR(64) DEFAULT '',
                suffix VARCHAR(64) DEFAULT '',
                color VARCHAR(32) DEFAULT '<white>',
                priority INT DEFAULT 0,
                is_vip BOOLEAN DEFAULT FALSE,
                is_staff BOOLEAN DEFAULT FALSE,
                permissions TEXT
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,

            // Permissions individuelles, éventuellement temporaires
            """
            CREATE TABLE IF NOT EXISTS tropicube_permissions (
                uuid VARCHAR(36) NOT NULL,
                permission VARCHAR(191) NOT NULL,
                value BOOLEAN DEFAULT TRUE,
                expiry BIGINT DEFAULT -1,
                granted_by VARCHAR(36),
                PRIMARY KEY (uuid, permission),
                INDEX idx_permissions_expiry (expiry),
                FOREIGN KEY (uuid) REFERENCES tropicube_players(uuid) ON DELETE CASCADE
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,

            """
            CREATE TABLE IF NOT EXISTS tropicube_sheepwars (
                uuid VARCHAR(36) PRIMARY KEY,
                username VARCHAR(16) NOT NULL,
                playerKit VARCHAR(16) NOT NULL
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """
        };

        try (Connection conn = getConnection()) {
            for (String sql : tables) {
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.execute();
                }
            }
            migrateLegacySchema(conn);
            migrateEconomyAmounts(conn);
            syncGradesFromConfig(conn);
            plugin.getLogger().info("[Tropicube-DB] Tables créées/vérifiées.");
        }
    }

    private void migrateEconomyAmounts(Connection conn) throws SQLException {
        ensureDecimalColumn(conn, "tropicube_economy", "balance", "DECIMAL(19,2) NOT NULL DEFAULT 0.00");
        ensureDecimalColumn(conn, "tropicube_economy", "total_earned", "DECIMAL(19,2) NOT NULL DEFAULT 0.00");
        ensureDecimalColumn(conn, "tropicube_economy", "total_spent", "DECIMAL(19,2) NOT NULL DEFAULT 0.00");
        ensureDecimalColumn(conn, "tropicube_transactions", "amount", "DECIMAL(19,2) NOT NULL");
    }

    private void ensureDecimalColumn(Connection conn, String table, String column, String definition) throws SQLException {
        try (ResultSet rs = conn.getMetaData().getColumns(conn.getCatalog(), null, table, column)) {
            if (rs.next() && rs.getInt("DATA_TYPE") == Types.DECIMAL && rs.getInt("COLUMN_SIZE") == 19
                    && rs.getInt("DECIMAL_DIGITS") == 2) {
                return;
            }
        }
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("ALTER TABLE " + table + " MODIFY COLUMN " + column + " " + definition);
        }
        plugin.getLogger().info("[Tropicube-DB] Colonne monétaire normalisée : " + table + "." + column);
    }

    /** Met à niveau les tables créées par les anciennes versions sans supprimer de données. */
    private void migrateLegacySchema(Connection conn) throws SQLException {
        boolean legacyPlayerRank = hasColumn(conn, "tropicube_players", "player_rank");
        boolean hadGradeColumn = hasColumn(conn, "tropicube_players", "grade");
        ensureColumn(conn, "tropicube_players", "grade", "VARCHAR(32) DEFAULT 'JOUEUR'");
        ensureColumn(conn, "tropicube_players", "grade_expiry", "BIGINT DEFAULT -1");
        ensureColumn(conn, "tropicube_grades", "is_vip", "BOOLEAN DEFAULT FALSE");
        ensureColumn(conn, "tropicube_grades", "is_staff", "BOOLEAN DEFAULT FALSE");
        ensureColumn(conn, "tropicube_grades", "permissions", "TEXT");

        if (legacyPlayerRank && !hadGradeColumn) {
            try (PreparedStatement stmt = conn.prepareStatement(
                    "UPDATE tropicube_players SET grade = player_rank " +
                    "WHERE player_rank IS NOT NULL AND (grade IS NULL OR grade = 'JOUEUR')")) {
                stmt.executeUpdate();
            }
        }
    }

    private void ensureColumn(Connection conn, String table, String column, String definition) throws SQLException {
        if (hasColumn(conn, table, column)) return;
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        }
        plugin.getLogger().info("[Tropicube-DB] Colonne ajoutée : " + table + "." + column);
    }

    private boolean hasColumn(Connection conn, String table, String column) throws SQLException {
        DatabaseMetaData metaData = conn.getMetaData();
        try (ResultSet rs = metaData.getColumns(conn.getCatalog(), null, table, column)) {
            if (rs.next()) return true;
        }
        // Certains pilotes traitent les noms de métadonnées en majuscules.
        try (ResultSet rs = metaData.getColumns(conn.getCatalog(), null,
                table.toUpperCase(Locale.ROOT), column.toUpperCase(Locale.ROOT))) {
            return rs.next();
        }
    }

    private void syncGradesFromConfig(Connection conn) throws SQLException {
        var gradesSection = plugin.getConfig().getConfigurationSection("grades");
        if (gradesSection == null) {
            plugin.getLogger().warning("[Tropicube-DB] Aucune section 'grades' dans config.yml — grades non synchronisés.");
            return;
        }

        String sql = """
            INSERT INTO tropicube_grades
                (name, display_name, prefix, suffix, color, priority, is_vip, is_staff, permissions)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                display_name = VALUES(display_name),
                prefix       = VALUES(prefix),
                suffix       = VALUES(suffix),
                color        = VALUES(color),
                priority     = VALUES(priority),
                is_vip       = VALUES(is_vip),
                is_staff     = VALUES(is_staff),
                permissions  = VALUES(permissions)
            """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (String gradeName : gradesSection.getKeys(false)) {
                var s = gradesSection.getConfigurationSection(gradeName);
                if (s == null) continue;
                String permsStr = String.join(",", s.getStringList("permissions"));
                stmt.setString(1, gradeName);
                stmt.setString(2, s.getString("display-name", gradeName));
                stmt.setString(3, s.getString("prefix", ""));
                stmt.setString(4, s.getString("suffix", ""));
                stmt.setString(5, s.getString("color", "<white>"));
                stmt.setInt(6, s.getInt("priority", 0));
                stmt.setBoolean(7, s.getBoolean("is-vip", false));
                stmt.setBoolean(8, s.getBoolean("is-staff", false));
                stmt.setString(9, permsStr);
                stmt.addBatch();
            }
            stmt.executeBatch();
        }
        plugin.getLogger().info("[Tropicube-DB] " + gradesSection.getKeys(false).size() + " grades synchronisés depuis config.yml.");
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public int executeUpdate(String sql, Object... params) {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                stmt.setObject(i + 1, params[i]);
            }
            return stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[Tropicube-DB] Erreur update: " + sql, e);
            throw new DatabaseOperationException("Échec de la mise à jour SQL", e);
        }
    }

    public <T> CompletableFuture<T> supplyAsync(SqlSupplier<T> operation) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return operation.get();
            } catch (SQLException e) {
                throw new DatabaseOperationException("Échec de l'opération SQL asynchrone", e);
            }
        }, executor);
    }

    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) executor.shutdownNow();
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            plugin.getLogger().info("[Tropicube-DB] Pool de connexions fermé.");
        }
    }

    public boolean isConnected() {
        return dataSource != null && !dataSource.isClosed();
    }

    @FunctionalInterface
    public interface SqlSupplier<T> {
        T get() throws SQLException;
    }

    public static class DatabaseOperationException extends RuntimeException {
        public DatabaseOperationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
