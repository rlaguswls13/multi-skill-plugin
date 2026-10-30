package com.msp.sqlinjection;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SqlInjectionDetectorTest {

    @Test
    @DisplayName("Safe queries and benign parameters pass check")
    void testSafeInputs() {
        assertTrue(SqlInjectionDetector.isSafe("john_doe"));
        assertTrue(SqlInjectionDetector.isSafe("apple-1234"));
        assertTrue(SqlInjectionDetector.isSafe("SELECT id, name FROM users WHERE id = 10"));
        assertDoesNotThrow(() -> SqlInjectionDetector.assertSafe("Normal search query for clothes"));
    }

    @Test
    @DisplayName("Detect boolean tautology attacks (' OR '1'='1)")
    void testTautology() {
        String attack = "admin' or '1'='1";
        SqlInjectionDetection det = SqlInjectionDetector.detect(attack);
        assertTrue(det.detected());
        assertEquals("TAUTOLOGY_OR", det.ruleName());
        assertThrows(SqlInjectionException.class, () -> SqlInjectionDetector.assertSafe(attack));
    }

    @Test
    @DisplayName("Detect UNION SELECT attack")
    void testUnionSelect() {
        String attack = "1' UNION SELECT username, password FROM members--";
        SqlInjectionDetection det = SqlInjectionDetector.detect(attack);
        assertTrue(det.detected());
    }

    @Test
    @DisplayName("Detect stacked queries (; DROP TABLE)")
    void testStackedQueries() {
        String attack = "test'; DROP TABLE users;";
        SqlInjectionDetection det = SqlInjectionDetector.detect(attack);
        assertTrue(det.detected());
        assertEquals("STACKED_QUERIES", det.ruleName());
    }

    @Test
    @DisplayName("Detect comment injection and command execution")
    void testCommentAndExec() {
        assertTrue(SqlInjectionDetector.detect("admin'--").detected());
        assertTrue(SqlInjectionDetector.detect("'; xp_cmdshell('dir')").detected());
    }
}
