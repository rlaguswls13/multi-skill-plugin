package com.msp.sqlsanitizer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SqlIdentifierSanitizer Tests")
class SqlIdentifierSanitizerTest {

    @Test
    @DisplayName("isValidIdentifier: valid simple identifiers return true")
    void testIsValidIdentifierPositive() {
        assertTrue(SqlIdentifierSanitizer.isValidIdentifier("users"));
        assertTrue(SqlIdentifierSanitizer.isValidIdentifier("user_id"));
        assertTrue(SqlIdentifierSanitizer.isValidIdentifier("_internal_seq"));
        assertTrue(SqlIdentifierSanitizer.isValidIdentifier("order123"));
        assertTrue(SqlIdentifierSanitizer.isValidIdentifier("A"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "   ",
            "1user",
            "user-name",
            "user.name",
            "users; DROP TABLE users;--",
            "users' OR 1=1",
            "users/*comment*/",
            "users\0name",
            "user name"
    })
    @DisplayName("isValidIdentifier: invalid or dangerous inputs return false")
    void testIsValidIdentifierNegative(String input) {
        assertFalse(SqlIdentifierSanitizer.isValidIdentifier(input));
    }

    @Test
    @DisplayName("isValidIdentifier: null and overlength identifiers return false")
    void testIsValidIdentifierEdgeCases() {
        assertFalse(SqlIdentifierSanitizer.isValidIdentifier(null));
        String overLength = "a".repeat(129);
        assertFalse(SqlIdentifierSanitizer.isValidIdentifier(overLength));
        String exactMaxLength = "a".repeat(128);
        assertTrue(SqlIdentifierSanitizer.isValidIdentifier(exactMaxLength));
    }

    @Test
    @DisplayName("sanitize: valid identifiers quoted correctly per dialect")
    void testSanitizeDialects() {
        assertEquals("\"users\"", SqlIdentifierSanitizer.sanitize("users", SqlDialect.ANSI_STANDARD));
        assertEquals("`users`", SqlIdentifierSanitizer.sanitize("users", SqlDialect.MYSQL));
        assertEquals("\"users\"", SqlIdentifierSanitizer.sanitize("users", SqlDialect.POSTGRESQL));
        assertEquals("\"users\"", SqlIdentifierSanitizer.sanitize("users", SqlDialect.ORACLE));
    }

    @Test
    @DisplayName("sanitize: reserved keywords safely quoted")
    void testReservedKeywordsQuoting() {
        assertTrue(SqlIdentifierSanitizer.isReservedKeyword("SELECT"));
        assertTrue(SqlIdentifierSanitizer.isReservedKeyword("order"));
        assertTrue(SqlIdentifierSanitizer.isReservedKeyword("group"));
        assertFalse(SqlIdentifierSanitizer.isReservedKeyword("my_column"));

        assertEquals("\"order\"", SqlIdentifierSanitizer.sanitize("order", SqlDialect.ANSI_STANDARD));
        assertEquals("`select`", SqlIdentifierSanitizer.sanitize("select", SqlDialect.MYSQL));
    }

    @Test
    @DisplayName("sanitize: inner quote characters properly escaped")
    void testInnerQuoteEscaping() {
        // ANSI double quote inside identifier
        String ansiResult = SqlIdentifierSanitizer.sanitize("col\"name", SqlDialect.ANSI_STANDARD);
        assertEquals("\"col\"\"name\"", ansiResult);

        // MySQL backtick inside identifier
        String mysqlResult = SqlIdentifierSanitizer.sanitize("col`name", SqlDialect.MYSQL);
        assertEquals("`col``name`", mysqlResult);
    }

    @Test
    @DisplayName("sanitizeQualified: schema.table and schema.table.column paths")
    void testSanitizeQualified() {
        assertEquals("\"my_schema\".\"users\"",
                SqlIdentifierSanitizer.sanitizeQualified("my_schema.users", SqlDialect.ANSI_STANDARD));
        assertEquals("`my_schema`.`users`",
                SqlIdentifierSanitizer.sanitizeQualified("my_schema.users", SqlDialect.MYSQL));
        assertEquals("\"db\".\"public\".\"users\"",
                SqlIdentifierSanitizer.sanitizeQualified("db.public.users", SqlDialect.POSTGRESQL));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            ".users",
            "users.",
            "my_schema..users",
            "  .  ",
            ""
    })
    @DisplayName("sanitizeQualified: malformed qualified names throw IllegalArgumentException")
    void testSanitizeQualifiedMalformed(String malformed) {
        assertThrows(IllegalArgumentException.class, () ->
                SqlIdentifierSanitizer.sanitizeQualified(malformed, SqlDialect.ANSI_STANDARD));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "users; DROP TABLE users;--",
            "users' OR 1=1",
            "users' OR '1'='1",
            "users/*comment*/",
            "users\0admin",
            "users; SELECT * FROM audit",
            "users = 1",
            "users<script>",
            "users(id)",
            "users\\admin"
    })
    @DisplayName("sanitize: injection attempts throw IllegalArgumentException")
    void testInjectionAttemptsRejected(String payload) {
        assertThrows(IllegalArgumentException.class, () ->
                SqlIdentifierSanitizer.sanitize(payload, SqlDialect.ANSI_STANDARD));
        assertThrows(IllegalArgumentException.class, () ->
                SqlIdentifierSanitizer.sanitize(payload, SqlDialect.MYSQL));
    }

    @Test
    @DisplayName("sanitize: unqualified identifier containing dot throws IllegalArgumentException")
    void testUnqualifiedWithDotThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                SqlIdentifierSanitizer.sanitize("schema.table", SqlDialect.ANSI_STANDARD));
    }

    @Test
    @DisplayName("sanitize: null or blank throws IllegalArgumentException")
    void testNullOrBlankThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                SqlIdentifierSanitizer.sanitize(null, SqlDialect.ANSI_STANDARD));
        assertThrows(IllegalArgumentException.class, () ->
                SqlIdentifierSanitizer.sanitize("   ", SqlDialect.ANSI_STANDARD));
        assertThrows(NullPointerException.class, () ->
                SqlIdentifierSanitizer.sanitize("users", null));
    }
}
