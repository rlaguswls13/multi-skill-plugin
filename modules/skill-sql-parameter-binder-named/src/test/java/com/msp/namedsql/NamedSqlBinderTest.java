package com.msp.namedsql;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class NamedSqlBinderTest {

    @Test
    @DisplayName("Single and multiple basic named parameters are parsed into '?' with correct 1-based indexing")
    void testBasicNamedParameters() {
        String sql = "SELECT * FROM users WHERE name = :name AND age >= :minAge AND status = :status";
        Map<String, Object> params = Map.of(
                "name", "Alice",
                "minAge", 25,
                "status", "ACTIVE"
        );

        ParsedNamedSql parsed = NamedSqlBinder.compile(sql, params);

        assertEquals("SELECT * FROM users WHERE name = ? AND age >= ? AND status = ?", parsed.preparedSql());
        assertEquals(List.of("name", "minAge", "status"), parsed.parameterOrder());
        assertEquals(List.of(1), parsed.parameterIndexMap().get("name"));
        assertEquals(List.of(2), parsed.parameterIndexMap().get("minAge"));
        assertEquals(List.of(3), parsed.parameterIndexMap().get("status"));
    }

    @Test
    @DisplayName("Repeated named parameters map to multiple index positions")
    void testRepeatedNamedParameter() {
        String sql = "SELECT * FROM items WHERE (:flag = TRUE AND price > :price) OR (:flag = FALSE AND discount > :price)";
        Map<String, Object> params = Map.of(
                "flag", true,
                "price", 1000
        );

        ParsedNamedSql parsed = NamedSqlBinder.compile(sql, params);

        assertEquals("SELECT * FROM items WHERE (? = TRUE AND price > ?) OR (? = FALSE AND discount > ?)", parsed.preparedSql());
        assertEquals(List.of("flag", "price", "flag", "price"), parsed.parameterOrder());
        assertEquals(List.of(1, 3), parsed.parameterIndexMap().get("flag"));
        assertEquals(List.of(2, 4), parsed.parameterIndexMap().get("price"));
    }

    @Test
    @DisplayName("Collection parameter expands into comma-separated '?' placeholders")
    void testInClauseCollectionExpansion() {
        String sql = "SELECT * FROM orders WHERE status = :status AND id IN (:orderIds)";
        Map<String, Object> params = Map.of(
                "status", "PENDING",
                "orderIds", List.of(101, 102, 103)
        );

        ParsedNamedSql parsed = NamedSqlBinder.compile(sql, params);

        assertEquals("SELECT * FROM orders WHERE status = ? AND id IN (?, ?, ?)", parsed.preparedSql());
        assertEquals(List.of("status", "orderIds", "orderIds", "orderIds"), parsed.parameterOrder());
        assertEquals(List.of(1), parsed.parameterIndexMap().get("status"));
        assertEquals(List.of(2, 3, 4), parsed.parameterIndexMap().get("orderIds"));
    }

    @Test
    @DisplayName("Array parameter expands into comma-separated '?' placeholders")
    void testInClauseArrayExpansion() {
        String sql = "SELECT * FROM items WHERE id IN (:ids)";
        Map<String, Object> params = Map.of("ids", new int[]{10, 20, 30});

        ParsedNamedSql parsed = NamedSqlBinder.compile(sql, params);

        assertEquals("SELECT * FROM items WHERE id IN (?, ?, ?)", parsed.preparedSql());
        assertEquals(List.of("ids", "ids", "ids"), parsed.parameterOrder());
        assertEquals(List.of(1, 2, 3), parsed.parameterIndexMap().get("ids"));
    }

    @Test
    @DisplayName("Empty collection parameter throws IllegalArgumentException")
    void testEmptyCollectionThrows() {
        String sql = "SELECT * FROM orders WHERE id IN (:ids)";
        Map<String, Object> params = Map.of("ids", List.of());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                NamedSqlBinder.compile(sql, params));
        assertTrue(ex.getMessage().contains("Collection parameter 'ids' cannot be empty"));
    }

    @Test
    @DisplayName("Empty array parameter throws IllegalArgumentException")
    void testEmptyArrayThrows() {
        String sql = "SELECT * FROM orders WHERE id IN (:ids)";
        Map<String, Object> params = Map.of("ids", new String[0]);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                NamedSqlBinder.compile(sql, params));
        assertTrue(ex.getMessage().contains("Array parameter 'ids' cannot be empty"));
    }

    @Test
    @DisplayName("Unsupplied parameter throws IllegalArgumentException")
    void testUnsuppliedParameterThrows() {
        String sql = "SELECT * FROM users WHERE name = :name AND email = :email";
        Map<String, Object> params = Map.of("name", "Bob");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                NamedSqlBinder.compile(sql, params));
        assertTrue(ex.getMessage().contains("Unsupplied parameter: 'email'"));
    }

    @Test
    @DisplayName("Colons in string literals and comments are safely ignored")
    void testStringLiteralAndCommentsProtection() {
        String sql = "SELECT 'Time is :not_param and it''s :cool' AS msg, -- comment with :param1\n" +
                "/* block comment with :param2 */ id FROM users WHERE id = :id";
        Map<String, Object> params = Map.of("id", 42);

        ParsedNamedSql parsed = NamedSqlBinder.compile(sql, params);

        assertTrue(parsed.preparedSql().contains("'Time is :not_param and it''s :cool' AS msg"));
        assertTrue(parsed.preparedSql().contains("-- comment with :param1\n"));
        assertTrue(parsed.preparedSql().contains("/* block comment with :param2 */"));
        assertTrue(parsed.preparedSql().endsWith("WHERE id = ?"));
        assertEquals(List.of("id"), parsed.parameterOrder());
        assertEquals(List.of(1), parsed.parameterIndexMap().get("id"));
    }

    @Test
    @DisplayName("PostgreSQL type casts (::type) are preserved and not parsed as parameters")
    void testPostgresTypeCastProtection() {
        String sql = "SELECT val::text, price::numeric(10,2), age::int FROM items WHERE id = :id AND created_at >= :since::date";
        Map<String, Object> params = Map.of(
                "id", 1,
                "since", "2026-01-01"
        );

        ParsedNamedSql parsed = NamedSqlBinder.compile(sql, params);

        assertEquals("SELECT val::text, price::numeric(10,2), age::int FROM items WHERE id = ? AND created_at >= ?::date",
                parsed.preparedSql());
        assertEquals(List.of("id", "since"), parsed.parameterOrder());
        assertEquals(List.of(1), parsed.parameterIndexMap().get("id"));
        assertEquals(List.of(2), parsed.parameterIndexMap().get("since"));
    }

    @Test
    @DisplayName("toDebugSql embeds formatted values into prepared SQL")
    void testToDebugSql() {
        String sql = "SELECT * FROM users WHERE name = ? AND age = ? AND active = ? AND created_date = ? AND note = ?";
        List<Object> values = java.util.Arrays.asList(
                "O'Connor",
                30,
                true,
                LocalDate.of(2026, 11, 1),
                null
        );

        String debugSql = NamedSqlBinder.toDebugSql(sql, values);

        assertEquals("SELECT * FROM users WHERE name = 'O''Connor' AND age = 30 AND active = TRUE AND created_date = '2026-11-01' AND note = NULL", debugSql);
    }

    @Test
    @DisplayName("toDebugSql throws exception on parameter count mismatch")
    void testToDebugSqlCountMismatch() {
        String sql = "SELECT * FROM users WHERE id = ? AND name = ?";
        assertThrows(IllegalArgumentException.class, () ->
                NamedSqlBinder.toDebugSql(sql, List.of(1)));

        assertThrows(IllegalArgumentException.class, () ->
                NamedSqlBinder.toDebugSql(sql, List.of(1, "Bob", "Extra")));
    }
}
