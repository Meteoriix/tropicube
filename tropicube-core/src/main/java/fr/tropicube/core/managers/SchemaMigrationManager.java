package fr.tropicube.core.managers;

import fr.tropicube.core.TropicubeCore;
import fr.tropicube.core.util.MessageStyle;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** Applies ordered, immutable SQL resources and records every successful version. */
final class SchemaMigrationManager {
    private static final String INDEX_RESOURCE = "/db/migration/index.txt";

    private final java.util.function.Consumer<String> log;

    SchemaMigrationManager(TropicubeCore plugin) {
        this.log = message -> plugin.getLogger().info(message);
    }

    SchemaMigrationManager(java.util.function.Consumer<String> log) { this.log = log; }

    void migrate(Connection connection) throws SQLException {
        createHistoryTable(connection);
        for (String resource : migrationResources()) {
            String version = resource.substring(0, resource.indexOf("__"));
            verifyChecksum(connection, version, resource);
            if (isApplied(connection, version)) continue;
            apply(connection, version, resource);
        }
    }

    /** Validates both successful and interrupted executions before any migration SQL is run. */
    private void verifyChecksum(Connection connection, String version, String resource) throws SQLException {
        String checksum = checksum(readResource("/db/migration/" + resource));
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT resource_name, checksum FROM tropicube_schema_checksums WHERE version = ?")) {
            statement.setString(1, version);
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    if (!resource.equals(result.getString(1)) || !checksum.equals(result.getString(2))) {
                        throw new SQLException("Migration changed after execution began: " + resource);
                    }
                    return;
                }
            }
        }
        if (isApplied(connection, version)) {
            java.util.Properties reference = new java.util.Properties();
            try (InputStream input = SchemaMigrationManager.class.getResourceAsStream("/db/migration/legacy-checksums.properties")) {
                if (input == null) throw new SQLException("Missing legacy migration checksums");
                reference.load(input);
            } catch (IOException failure) { throw new SQLException("Cannot read legacy checksums", failure); }
            if (!checksum.equals(reference.getProperty(resource))) {
                throw new SQLException("Legacy migration requires reviewed reference checksum: " + resource);
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT resource_name FROM tropicube_schema_migrations WHERE version = ?")) {
                statement.setString(1, version);
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next() || !resource.equals(result.getString(1))) throw new SQLException("Legacy resource mismatch: " + version);
                }
            }
        }
        // A separate durable row survives MySQL DDL implicit commits and interrupted migrations.
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO tropicube_schema_checksums(version, resource_name, checksum) VALUES (?, ?, ?)")) {
            statement.setString(1, version);
            statement.setString(2, resource);
            statement.setString(3, checksum);
            statement.executeUpdate();
        }
    }

    static String checksum(String sql) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(sql.replace("\r\n", "\n").getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private void createHistoryTable(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS tropicube_schema_checksums (
                        version VARCHAR(32) PRIMARY KEY,
                        resource_name VARCHAR(191) NOT NULL,
                        checksum CHAR(64) NOT NULL
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS tropicube_schema_migrations (
                        version VARCHAR(32) PRIMARY KEY,
                        resource_name VARCHAR(191) NOT NULL,
                        applied_at BIGINT NOT NULL
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
        }
    }

    private List<String> migrationResources() throws SQLException {
        InputStream input = SchemaMigrationManager.class.getResourceAsStream(INDEX_RESOURCE);
        if (input == null) throw new SQLException("Index de migrations introuvable: " + INDEX_RESOURCE);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            return readIndex(reader.lines().toList());
        } catch (IOException error) {
            throw new SQLException("Impossible de lire l'index de migrations", error);
        }
    }

    static List<String> readIndex(List<String> lines) {
        List<String> resources = lines.stream().map(String::trim)
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .toList();
        if (resources.stream().anyMatch(name -> !name.matches("V[0-9]{3}__[a-z0-9_]+\\.sql"))) {
            throw new IllegalArgumentException("Invalid migration filename");
        }
        if (resources.size() != resources.stream().map(name -> name.split("__")[0]).distinct().count()) {
            throw new IllegalArgumentException("L'index des migrations contient un doublon");
        }
        List<String> sorted = resources.stream().sorted().toList();
        if (!resources.equals(sorted)) throw new IllegalArgumentException("L'index des migrations n'est pas ordonné");
        return resources;
    }

    private boolean isApplied(Connection connection, String version) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM tropicube_schema_migrations WHERE version = ?")) {
            statement.setString(1, version);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private void apply(Connection connection, String version, String resource) throws SQLException {
        String sql = readResource("/db/migration/" + resource);
        List<String> statements = splitStatements(sql);
        try {
            for (String statementSql : statements) {
                try (Statement statement = connection.createStatement()) {
                    statement.execute(statementSql);
                }
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO tropicube_schema_migrations(version, resource_name, applied_at) VALUES (?, ?, ?)")) {
                statement.setString(1, version);
                statement.setString(2, resource);
                statement.setLong(3, System.currentTimeMillis());
                statement.executeUpdate();
            }
            log.accept(MessageStyle.log("tc", "DB", "<gray>Migration appliquée: " + resource));
        } catch (SQLException error) {
            throw new SQLException("Échec de la migration " + resource, error);
        }
    }

    private String readResource(String resource) throws SQLException {
        InputStream input = SchemaMigrationManager.class.getResourceAsStream(resource);
        if (input == null) throw new SQLException("Migration introuvable: " + resource);
        try (input) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new SQLException("Impossible de lire " + resource, error);
        }
    }

    static List<String> splitStatements(String script) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String rawLine : script.replace("\r", "").split("\n")) {
            String line = rawLine.strip();
            if (line.isEmpty() || line.startsWith("--")) continue;
            current.append(rawLine).append('\n');
            if (line.endsWith(";")) {
                String value = current.toString().strip();
                statements.add(value.substring(0, value.length() - 1));
                current.setLength(0);
            }
        }
        if (!current.toString().isBlank()) statements.add(current.toString().strip());
        return List.copyOf(statements);
    }
}
