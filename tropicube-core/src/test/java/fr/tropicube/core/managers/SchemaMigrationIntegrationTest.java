package fr.tropicube.core.managers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.sql.DriverManager;
import static org.junit.jupiter.api.Assertions.*;

/** Runs only against the disposable database created by tools/ops/integration_tests.py. */
@EnabledIfEnvironmentVariable(named = "TROPICUBE_TEST_MYSQL_URL", matches = ".+")
class SchemaMigrationIntegrationTest {
    @Test void migratesFreshDatabaseResumesAndDetectsTampering() throws Exception {
        String url = System.getenv("TROPICUBE_TEST_MYSQL_URL");
        assertTrue(url.contains("/tropicube_integration"), "Refuse any non-test database");
        try (var connection = DriverManager.getConnection(url, "tropicube_test", "integration-only")) {
            for (String sql : DatabaseSchema.baseStatements()) {
                try (var statement = connection.createStatement()) { statement.execute(sql); }
            }
            var migrations = new SchemaMigrationManager(message -> { });
            migrations.migrate(connection);
            migrations.migrate(connection);
            try (var statement = connection.createStatement()) {
                try (var result = statement.executeQuery("SELECT COUNT(*) FROM tropicube_schema_migrations")) {
                    assertTrue(result.next()); assertEquals(8, result.getInt(1));
                }
                // Simulate an old installation without recorded fingerprints.
                statement.executeUpdate("DELETE FROM tropicube_schema_checksums");
                migrations.migrate(connection);
                // V008 is designed to resume after its DDL has already been applied.
                statement.executeUpdate("DELETE FROM tropicube_schema_migrations WHERE version='V008'");
                migrations.migrate(connection);
                statement.executeUpdate("UPDATE tropicube_schema_checksums SET checksum=REPEAT('0',64) WHERE version='V008'");
                assertThrows(java.sql.SQLException.class, () -> migrations.migrate(connection));
            }
        }
    }

    @Test void schemaLockSerializesBackupAndMigrations() throws Exception {
        String url = System.getenv("TROPICUBE_TEST_MYSQL_URL");
        try (var first = DriverManager.getConnection(url, "tropicube_test", "integration-only");
             var second = DriverManager.getConnection(url, "tropicube_test", "integration-only");
             var a = first.createStatement(); var b = second.createStatement()) {
            try (var result = a.executeQuery("SELECT GET_LOCK('tropicube:core:schema',0)")) {
                assertTrue(result.next()); assertEquals(1, result.getInt(1));
            }
            try (var result = b.executeQuery("SELECT GET_LOCK('tropicube:core:schema',0)")) {
                assertTrue(result.next()); assertEquals(0, result.getInt(1));
            }
            a.execute("DO RELEASE_LOCK('tropicube:core:schema')");
            try (var result = b.executeQuery("SELECT GET_LOCK('tropicube:core:schema',0)")) {
                assertTrue(result.next()); assertEquals(1, result.getInt(1));
            }
        }
    }
}
