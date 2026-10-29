package com.msp.slowquery;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SqlNormalizerTest {

    @Test
    @DisplayName("Normalizes string literals and numbers into ? parameters")
    void testLiteralNormalization() {
        String sql = "SELECT * FROM users WHERE id = 42 AND name = 'Alice' AND active = 1";
        String normalized = SqlNormalizer.normalize(sql);
        assertEquals("SELECT * FROM users WHERE id = ? AND name = ? AND active = ?", normalized);
    }

    @Test
    @DisplayName("Normalizes floating point numbers and escaped strings")
    void testFloatsAndEscapedStrings() {
        String sql = "UPDATE products SET price = 99.95, desc = 'It\\'s awesome' WHERE id = 101";
        String normalized = SqlNormalizer.normalize(sql);
        assertEquals("UPDATE products SET price = ?, desc = ? WHERE id = ?", normalized);
    }

    @Test
    @DisplayName("Collapses IN clauses with multiple parameters")
    void testInClauseCollapse() {
        String sql = "SELECT * FROM orders WHERE status IN ('PENDING', 'PROCESSING', 'SHIPPED')";
        String normalized = SqlNormalizer.normalize(sql);
        assertEquals("SELECT * FROM orders WHERE status IN (?)", normalized);
    }

    @Test
    @DisplayName("Preserves identifiers containing numbers")
    void testIdentifiersWithNumbers() {
        String sql = "SELECT col1, user_2_id FROM table_3 WHERE count >= 5";
        String normalized = SqlNormalizer.normalize(sql);
        assertEquals("SELECT col1, user_2_id FROM table_3 WHERE count >= ?", normalized);
    }

    @Test
    @DisplayName("Collapses multiple whitespaces and newlines")
    void testWhitespaceCollapsing() {
        String sql = """
                SELECT   id,    name
                FROM     users
                WHERE    role = 'ADMIN'
                """;
        String normalized = SqlNormalizer.normalize(sql);
        assertEquals("SELECT id, name FROM users WHERE role = ?", normalized);
    }
}
