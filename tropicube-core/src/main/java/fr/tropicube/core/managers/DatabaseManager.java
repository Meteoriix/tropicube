package fr.tropicube.core.managers;

import fr.tropicube.core.util.MessageStyle;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import fr.tropicube.core.TropicubeCore;

import java.sql.*;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * MySQL database manager with HikariCP connection pool.
 * Manages table creation and provides utility methods.
 */
public class DatabaseManager {

    private static final String SCHEMA_LOCK_NAME = "tropicube:core:schema";
    private static final int SCHEMA_LOCK_TIMEOUT_SECONDS = 25;

    private final TropicubeCore plugin;

    private HikariDataSource dataSource;
    private final BoundedDatabaseExecutor executor;
    private final DatabaseOptions options;

    public DatabaseManager(TropicubeCore plugin) {
        this.plugin = plugin;
        options = DatabaseOptions.read((key, fallback) -> plugin.getConfig().getInt("database." + key, fallback));
        executor = new BoundedDatabaseExecutor(options.maxConcurrent(), options.queueCapacity());
    }

    public void initialize() throws SQLException {
        HikariConfig config = new HikariConfig();

        config.setJdbcUrl("jdbc:mysql://" +
                plugin.getConfiguredString("TROPICUBE_DB_HOST", "database.host", "localhost") + ":" +
                plugin.getConfiguredInt("TROPICUBE_DB_PORT", "database.port", 3306) + "/" +
                plugin.getConfiguredString("TROPICUBE_DB_NAME", "database.name", "tropicube") +
                "?useSSL=false&characterEncoding=UTF-8&socketTimeout=" + options.socketTimeoutMillis());
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        config.setUsername(plugin.getConfiguredString("TROPICUBE_DB_USER", "database.user", "root"));
        config.setPassword(plugin.getConfiguredString("TROPICUBE_DB_PASSWORD", "database.password", ""));
        config.setMaximumPoolSize(options.maxPoolSize());
        config.setMinimumIdle(options.minIdle());
        config.setConnectionTimeout(options.connectionTimeoutMillis());
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
        String[] tables = DatabaseSchema.baseStatements();

        try (Connection conn = getConnection();
             DatabaseSchemaLock ignored = DatabaseSchemaLock.acquire(
                     conn, SCHEMA_LOCK_NAME, SCHEMA_LOCK_TIMEOUT_SECONDS)) {
            for (String sql : tables) {
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.execute();
                }
            }
            migrateLegacySchema(conn);
            migrateEconomyAmounts(conn);
            new SchemaMigrationManager(plugin).migrate(conn);
            syncGradesFromConfig(conn);
            plugin.getLogger().info(MessageStyle.log("tc", "DB", "<gray>Tables créées/vérifiées."));
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
        plugin.getLogger().info(MessageStyle.log("tc", "DB", "<gray>Colonne monétaire normalisée : " + table + "." + column));
    }

    /** Upgrades tables created by older versions without deleting data. */
    private void migrateLegacySchema(Connection conn) throws SQLException {
        boolean legacyPlayerRank = hasColumn(conn, "tropicube_players", "player_rank");
        boolean hadGradeColumn = hasColumn(conn, "tropicube_players", "grade");
        ensureColumn(conn, "tropicube_players", "grade", "VARCHAR(32) DEFAULT 'JOUEUR'");
        ensureColumn(conn, "tropicube_players", "grade_expiry", "BIGINT DEFAULT -1");
        ensureColumn(conn, "tropicube_players", "vip_level", "SMALLINT NOT NULL DEFAULT 0");
        ensureColumn(conn, "tropicube_players", "mod_level", "SMALLINT NOT NULL DEFAULT 0");
        ensureColumn(conn, "tropicube_players", "access_revision", "BIGINT NOT NULL DEFAULT 0");
        ensureColumn(conn, "tropicube_grades", "default_vip_level", "SMALLINT NOT NULL DEFAULT 0");
        ensureColumn(conn, "tropicube_grades", "default_mod_level", "SMALLINT NOT NULL DEFAULT 0");

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
        plugin.getLogger().info(MessageStyle.log("tc", "DB", "<gray>Colonne ajoutée : " + table + "." + column));
    }

    private boolean hasColumn(Connection conn, String table, String column) throws SQLException {
        DatabaseMetaData metaData = conn.getMetaData();
        try (ResultSet rs = metaData.getColumns(conn.getCatalog(), null, table, column)) {
            if (rs.next()) return true;
        }
        // Some drivers treat metadata names in uppercase.
        try (ResultSet rs = metaData.getColumns(conn.getCatalog(), null,
                table.toUpperCase(Locale.ROOT), column.toUpperCase(Locale.ROOT))) {
            return rs.next();
        }
    }

    private void syncGradesFromConfig(Connection conn) throws SQLException {
        var gradesSection = plugin.getConfig().getConfigurationSection("grades");
        if (gradesSection == null) {
            plugin.getLogger().warning(MessageStyle.log("tc", "DB", "<yellow>Aucune section 'grades' dans config.yml — grades non synchronisés."));
            return;
        }

        String sql = """
            INSERT INTO tropicube_grades
                (name, display_name, prefix, suffix, color, priority, default_vip_level, default_mod_level)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                display_name = VALUES(display_name),
                prefix       = VALUES(prefix),
                suffix       = VALUES(suffix),
                color        = VALUES(color),
                priority          = VALUES(priority),
                default_vip_level = VALUES(default_vip_level),
                default_mod_level = VALUES(default_mod_level)
            """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (String gradeName : gradesSection.getKeys(false)) {
                var s = gradesSection.getConfigurationSection(gradeName);
                if (s == null) continue;
                stmt.setString(1, gradeName);
                stmt.setString(2, s.getString("display-name", gradeName));
                stmt.setString(3, s.getString("prefix", ""));
                stmt.setString(4, s.getString("suffix", ""));
                stmt.setString(5, s.getString("color", "<white>"));
                stmt.setInt(6, s.getInt("priority", 0));
                int vipLevel = s.getInt("default-vip-level", 0);
                int modLevel = s.getInt("default-mod-level", 0);
                if (vipLevel < 0 || vipLevel > 3 || modLevel < 0 || modLevel > 4) {
                    throw new SQLException("Niveaux invalides pour le grade " + gradeName);
                }
                stmt.setInt(7, vipLevel);
                stmt.setInt(8, modLevel);
                stmt.addBatch();
            }
            stmt.executeBatch();
        }
        plugin.getLogger().info(MessageStyle.log("tc", "DB", "<gray>" + gradesSection.getKeys(false).size() + " grades synchronisés depuis config.yml."));
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
            plugin.getLogger().log(Level.SEVERE, MessageStyle.log("tc", "DB", "<red>Erreur update: " + sql), e);
            throw new DatabaseOperationException("Échec de la mise à jour SQL", e);
        }
    }

    public <T> CompletableFuture<T> supplyAsync(SqlSupplier<T> operation) {
        return executor.submit(() -> {
            try {
                return operation.get();
            } catch (SQLException e) {
                throw new DatabaseOperationException("Échec de l'opération SQL asynchrone", e);
            }
        });
    }

    /** Bounded asynchronous execution for adapters whose operation has no return value. */
    public CompletableFuture<Void> runAsync(Runnable operation) {
        return supplyAsync(() -> { operation.run(); return null; });
    }

    public void close() {
        executor.close(java.time.Duration.ofSeconds(options.shutdownTimeoutSeconds()));
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            plugin.getLogger().info(MessageStyle.log("tc", "DB", "<gray>Pool de connexions fermé."));
        }
    }

    public int activeOperations() { return executor.activeCount(); }
    public int queuedOperations() { return executor.queuedCount(); }

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

    /**
     * Holds a MySQL connection-level advisory lock while the shared schema is prepared.
     * Dynamic Paper instances start concurrently, so metadata checks followed by DDL
     * must never run independently on several servers.
     */
    private static final class DatabaseSchemaLock implements AutoCloseable {
        private final Connection connection;
        private final String name;

        private DatabaseSchemaLock(Connection connection, String name) {
            this.connection = connection;
            this.name = name;
        }

        private static DatabaseSchemaLock acquire(Connection connection, String name, int timeoutSeconds)
                throws SQLException {
            try (PreparedStatement statement = connection.prepareStatement("SELECT GET_LOCK(?, ?)")) {
                statement.setString(1, name);
                statement.setInt(2, timeoutSeconds);
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next() || result.getInt(1) != 1) {
                        throw new SQLException("Impossible d'acquérir le verrou MySQL du schéma '" + name
                                + "' sous " + timeoutSeconds + " secondes");
                    }
                }
            }
            return new DatabaseSchemaLock(connection, name);
        }

        @Override
        public void close() throws SQLException {
            try (PreparedStatement statement = connection.prepareStatement("SELECT RELEASE_LOCK(?)")) {
                statement.setString(1, name);
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next() || result.getInt(1) != 1) {
                        throw new SQLException("Impossible de libérer le verrou MySQL du schéma '" + name + "'");
                    }
                }
            }
        }
    }
}
