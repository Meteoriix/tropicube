package fr.tropicube.core.managers;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SchemaMigrationManagerTest {
    @Test
    void splitsStatementsAndIgnoresComments() {
        assertEquals(List.of("CREATE TABLE first (id INT)", "CREATE TABLE second (id INT)"),
                SchemaMigrationManager.splitStatements("""
                        -- migration comment
                        CREATE TABLE first (id INT);

                        CREATE TABLE second (id INT);
                        """));
    }

    @Test
    void indexesEverySqlMigrationInOrder() throws Exception {
        var directory = Path.of(SchemaMigrationManagerTest.class.getResource("/db/migration").toURI());
        List<String> sqlFiles;
        try (var files = Files.list(directory)) {
            sqlFiles = files.map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".sql")).sorted().toList();
        }
        List<String> index = SchemaMigrationManager.readIndex(Files.readAllLines(directory.resolve("index.txt")));
        assertEquals(sqlFiles, index);
    }

    @Test
    void migrationsAvoidConditionalAddColumnUnsupportedByMysql() throws Exception {
        var directory = Path.of(SchemaMigrationManagerTest.class.getResource("/db/migration").toURI());
        try (var files = Files.list(directory)) {
            for (Path migration : files.filter(path -> path.toString().endsWith(".sql")).toList()) {
                String sql = Files.readString(migration);
                assertFalse(sql.matches("(?is).*ADD\\s+(?:COLUMN\\s+)?IF\\s+NOT\\s+EXISTS.*"),
                        () -> migration.getFileName() + " utilise une syntaxe non prise en charge par MySQL");
            }
        }
    }
}
