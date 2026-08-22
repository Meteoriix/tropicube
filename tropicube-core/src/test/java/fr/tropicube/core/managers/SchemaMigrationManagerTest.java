package fr.tropicube.core.managers;

import org.junit.jupiter.api.Test;

import java.util.List;

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
}
