package com.msp.sqllexer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SqlLexerTest {

    @Test
    @DisplayName("Tokenize standard SELECT statement")
    void testStandardSelect() {
        String sql = "SELECT id, name, age FROM users WHERE age >= 21 AND status = 'ACTIVE';";
        List<SqlToken> tokens = SqlLexer.tokenize(sql);

        assertFalse(tokens.isEmpty());

        assertEquals(SqlTokenType.KEYWORD, tokens.get(0).type());
        assertEquals("SELECT", tokens.get(0).value());
        assertEquals(0, tokens.get(0).position());

        assertEquals(SqlTokenType.IDENTIFIER, tokens.get(1).type());
        assertEquals("id", tokens.get(1).value());

        assertEquals(SqlTokenType.PUNCTUATION, tokens.get(2).type());
        assertEquals(",", tokens.get(2).value());

        assertEquals(SqlTokenType.IDENTIFIER, tokens.get(3).type());
        assertEquals("name", tokens.get(3).value());

        // Find WHERE token
        SqlToken whereToken = tokens.stream()
            .filter(t -> t.matchesIgnoreCase("WHERE"))
            .findFirst()
            .orElseThrow();
        assertEquals(SqlTokenType.KEYWORD, whereToken.type());

        // Check '>=' operator
        SqlToken gteToken = tokens.stream()
            .filter(t -> t.value().equals(">="))
            .findFirst()
            .orElseThrow();
        assertEquals(SqlTokenType.OPERATOR, gteToken.type());

        // Check numeric literal '21'
        SqlToken numToken = tokens.stream()
            .filter(t -> t.value().equals("21"))
            .findFirst()
            .orElseThrow();
        assertEquals(SqlTokenType.NUMERIC_LITERAL, numToken.type());

        // Check string literal 'ACTIVE'
        SqlToken strToken = tokens.stream()
            .filter(t -> t.value().equals("'ACTIVE'"))
            .findFirst()
            .orElseThrow();
        assertEquals(SqlTokenType.STRING_LITERAL, strToken.type());

        // Check semicolon punctuation
        SqlToken semiToken = tokens.getLast();
        assertEquals(SqlTokenType.PUNCTUATION, semiToken.type());
        assertEquals(";", semiToken.value());
    }

    @Test
    @DisplayName("Tokenize comments: --, #, and /* ... */")
    void testComments() {
        String sql = """
            -- Line comment 1
            SELECT * FROM users /* block comment */
            # Line comment 2
            WHERE id = 1
            """;

        List<SqlToken> tokens = SqlLexer.tokenize(sql);
        long commentCount = tokens.stream().filter(t -> t.type() == SqlTokenType.COMMENT).count();
        assertEquals(3, commentCount);

        List<SqlToken> filtered = SqlLexer.tokenizeWithoutComments(sql);
        assertTrue(filtered.stream().noneMatch(t -> t.type() == SqlTokenType.COMMENT));
    }

    @Test
    @DisplayName("Tokenize various operators: !=, <>, ||, :=")
    void testOperators() {
        String sql = "a != b AND c <> d AND e || f AND g := 10";
        List<SqlToken> tokens = SqlLexer.tokenize(sql);

        List<String> operators = tokens.stream()
            .filter(t -> t.type() == SqlTokenType.OPERATOR)
            .map(SqlToken::value)
            .toList();

        assertEquals(List.of("!=", "<>", "||", ":="), operators);
    }

    @Test
    @DisplayName("Tokenize quoted identifiers: backtick and bracket")
    void testQuotedIdentifiers() {
        String sql = "SELECT `order_id`, [customer_name] FROM `orders`";
        List<SqlToken> tokens = SqlLexer.tokenize(sql);

        List<SqlToken> identifiers = tokens.stream()
            .filter(t -> t.type() == SqlTokenType.IDENTIFIER)
            .toList();

        assertEquals(3, identifiers.size());
        assertEquals("`order_id`", identifiers.get(0).value());
        assertEquals("[customer_name]", identifiers.get(1).value());
        assertEquals("`orders`", identifiers.get(2).value());
    }

    @Test
    @DisplayName("Tokenize numeric formats: integer, decimal, scientific, hex")
    void testNumericLiterals() {
        String sql = "SELECT 100, 12.34, 1.5e-3, 0x1A2F FROM t";
        List<SqlToken> tokens = SqlLexer.tokenize(sql);

        List<String> numbers = tokens.stream()
            .filter(t -> t.type() == SqlTokenType.NUMERIC_LITERAL)
            .map(SqlToken::value)
            .toList();

        assertEquals(List.of("100", "12.34", "1.5e-3", "0x1A2F"), numbers);
    }

    @Test
    @DisplayName("Tokenize string literals with escaped quotes")
    void testEscapedStringLiterals() {
        String sql = "SELECT 'O''Reilly', \"John \\\"Doe\\\"\" FROM authors";
        List<SqlToken> tokens = SqlLexer.tokenize(sql);

        List<String> strings = tokens.stream()
            .filter(t -> t.type() == SqlTokenType.STRING_LITERAL)
            .map(SqlToken::value)
            .toList();

        assertEquals(List.of("'O''Reilly'", "\"John \\\"Doe\\\"\""), strings);
    }

    @Test
    @DisplayName("Dot resolution: table.column does not confuse with float")
    void testTableDotColumn() {
        String sql = "SELECT u.id, u.created_at FROM users u";
        List<SqlToken> tokens = SqlLexer.tokenize(sql);

        assertEquals("u", tokens.get(1).value());
        assertEquals(SqlTokenType.IDENTIFIER, tokens.get(1).type());

        assertEquals(".", tokens.get(2).value());
        assertEquals(SqlTokenType.PUNCTUATION, tokens.get(2).type());

        assertEquals("id", tokens.get(3).value());
        assertEquals(SqlTokenType.IDENTIFIER, tokens.get(3).type());
    }

    @Test
    @DisplayName("Empty or null input produces empty token list")
    void testEmptyInput() {
        assertTrue(SqlLexer.tokenize(null).isEmpty());
        assertTrue(SqlLexer.tokenize("").isEmpty());
        assertTrue(SqlLexer.tokenize("   \n\t  ").isEmpty());
    }
}
