package fr.tropicube.core.managers;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
