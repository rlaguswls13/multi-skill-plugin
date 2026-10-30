package com.msp.csvsqlite;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CsvSqliteConverterTest {

    private final CsvSqliteConverter converter = new CsvSqliteConverter();

    @Test
    @DisplayName("Infers types and generates DDL and batch INSERTs properly")
    void testBasicConversion() throws IOException {
        String csv = """
                id,name,balance,active
                1,Alice,1250.50,active
                2,Bob,450.00,inactive
                3,Charlie,99.99,pending
                """;

        ByteArrayInputStream in = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));
        String sql = converter.generateSqlScript(in, "users");

        assertNotNull(sql);
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS [users] ([id] INTEGER, [name] TEXT, [balance] REAL, [active] TEXT);"));
        assertTrue(sql.contains("BEGIN TRANSACTION;"));
        assertTrue(sql.contains("INSERT INTO [users] ([id], [name], [balance], [active]) VALUES"));
        assertTrue(sql.contains("(1, 'Alice', 1250.50, 'active')"));
        assertTrue(sql.contains("(2, 'Bob', 450.00, 'inactive')"));
        assertTrue(sql.contains("COMMIT;"));
    }

    @Test
    @DisplayName("Handles quoted strings with commas and escaped quotes")
    void testQuotedStringsAndEscaping() throws IOException {
        String csv = """
                id,description,rating
                101,"Product with, comma",4.5
                102,"He said ""Hello"" to all",5.0
                """;

        ByteArrayInputStream in = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));
        String sql = converter.generateSqlScript(in, "products");

        assertTrue(sql.contains("'Product with, comma'"));
        assertTrue(sql.contains("'He said \"Hello\" to all'"));
    }

    @Test
    @DisplayName("Streaming convert receives all SQL statements")
    void testStreamingConvert() throws IOException {
        String csv = """
                user_id,score
                1,100
                2,200
                3,300
                4,400
                """;

        List<String> statements = new ArrayList<>();
        converter.convert(
                new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)),
                "scores",
                2, // batch size = 2
                statements::add
        );

        // Expected: 1 CREATE TABLE, 1 BEGIN TRANSACTION, 2 batch INSERTs, 1 COMMIT
        assertEquals(5, statements.size());
        assertTrue(statements.get(0).startsWith("CREATE TABLE"));
        assertEquals("BEGIN TRANSACTION;", statements.get(1));
        assertTrue(statements.get(2).contains("(1, 100)") && statements.get(2).contains("(2, 200)"));
        assertTrue(statements.get(3).contains("(3, 300)") && statements.get(3).contains("(4, 400)"));
        assertEquals("COMMIT;", statements.get(4));
    }

    @Test
    @DisplayName("Handles empty values as NULL in numeric columns")
    void testEmptyNumericValuesAsNull() throws IOException {
        String csv = """
                id,age,salary
                1,25,50000
                2,,60000
                3,40,
                """;

        String sql = converter.generateSqlScript(
                new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)),
                "employees"
        );

        assertTrue(sql.contains("(2, NULL, 60000)"));
        assertTrue(sql.contains("(3, 40, NULL)"));
    }

    @Test
    @DisplayName("Handles multiline quoted CSV fields")
    void testMultilineQuotedFields() throws IOException {
        String csv = "id,bio\n1,\"Line 1\nLine 2\"\n2,\"Single line\"\n";

        String sql = converter.generateSqlScript(
                new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)),
                "bios"
        );

        assertTrue(sql.contains("'Line 1\nLine 2'"));
    }
}
