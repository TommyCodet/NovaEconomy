package de.txmmy.novaeconomy.database;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DatabaseTest {
    @Test
    void splitsMigrationStatementsAndSkipsComments() {
        String script = """
                -- Kommentar
                CREATE TABLE test (id INTEGER, name TEXT DEFAULT 'a;b');

                INSERT INTO test(id, name) VALUES (1, "x;y");
                """;

        List<String> statements = Database.splitSqlStatements(script);
        assertEquals(2, statements.size());
        assertEquals("CREATE TABLE test (id INTEGER, name TEXT DEFAULT 'a;b')", statements.get(0));
        assertEquals("INSERT INTO test(id, name) VALUES (1, \"x;y\")", statements.get(1));
    }
}
