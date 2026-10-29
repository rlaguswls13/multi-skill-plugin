package com.msp.sqlformat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SqlFormatterTest {

    @Test
    @DisplayName("Complex SELECT query with JOINs and subqueries formatting")
    void testComplexSelectWithJoinAndSubquery() {
        String rawSql = "select u.id, u.name, u.email from users u " +
                "left join orders o on u.id = o.user_id " +
                "where u.dept_id in (select d.id from departments d where d.active = 1) " +
                "and o.status = 'PENDING' order by u.id desc";

        String formatted = SqlFormatter.format(rawSql);

        assertTrue(formatted.contains("SELECT\n    u.id,\n    u.name,\n    u.email"));
        assertTrue(formatted.contains("FROM users u"));
        assertTrue(formatted.contains("LEFT JOIN orders o ON u.id = o.user_id"));
        assertTrue(formatted.contains("WHERE\n    u.dept_id IN ("));
        assertTrue(formatted.contains("SELECT\n            d.id"));
        assertTrue(formatted.contains("FROM departments d"));
        assertTrue(formatted.contains("WHERE\n            d.active = 1"));
        assertTrue(formatted.contains("AND o.status = 'PENDING'"));
        assertTrue(formatted.contains("ORDER BY\n    u.id DESC"));
    }

    @Test
    @DisplayName("Preserves string literal casing, spacing, and inner keywords")
    void testStringLiteralPreservation() {
        String rawSql = "SELECT id FROM logs WHERE message = 'select * from users where id = 1' AND note = 'Do Not Capitalize'";
        String formatted = SqlFormatter.format(rawSql);

        assertTrue(formatted.contains("'select * from users where id = 1'"));
        assertTrue(formatted.contains("'Do Not Capitalize'"));
    }

    @Test
    @DisplayName("Preserves escaped quotes inside string literals")
    void testEscapedQuotesInString() {
        String rawSql = "SELECT id FROM items WHERE description = 'It''s a special item' AND code = 'test\\''";
        String formatted = SqlFormatter.format(rawSql);

        assertTrue(formatted.contains("'It''s a special item'"));
        assertTrue(formatted.contains("'test\\''"));
    }

    @Test
    @DisplayName("INSERT INTO and VALUES formatting")
    void testInsertIntoFormatting() {
        String rawSql = "insert into users (id, name, email) values (1, 'Alice', 'alice@test.com')";
        String formatted = SqlFormatter.format(rawSql);

        assertTrue(formatted.contains("INSERT INTO users (id, name, email)"));
        assertTrue(formatted.contains("VALUES (1, 'Alice', 'alice@test.com')"));
    }

    @Test
    @DisplayName("UPDATE and SET formatting")
    void testUpdateFormatting() {
        String rawSql = "update users set name = 'Bob', status = 'ACTIVE' where id = 10";
        String formatted = SqlFormatter.format(rawSql);

        assertTrue(formatted.contains("UPDATE users"));
        assertTrue(formatted.contains("SET\n    name = 'Bob',\n    status = 'ACTIVE'"));
        assertTrue(formatted.contains("WHERE\n    id = 10"));
    }

    @Test
    @DisplayName("DELETE FROM formatting")
    void testDeleteFormatting() {
        String rawSql = "delete from users where active = 0 and last_login is null";
        String formatted = SqlFormatter.format(rawSql);

        assertTrue(formatted.contains("DELETE FROM users"));
        assertTrue(formatted.contains("WHERE\n    active = 0\n    AND last_login IS NULL"));
    }

    @Test
    @DisplayName("Preserves comments in SQL query")
    void testCommentPreservation() {
        String rawSql = "-- Line comment before query\nSELECT id /* inline block comment */ FROM users";
        String formatted = SqlFormatter.format(rawSql);

        assertTrue(formatted.contains("-- Line comment before query"));
        assertTrue(formatted.contains("/* inline block comment */"));
        assertTrue(formatted.contains("FROM users"));
    }

    @Test
    @DisplayName("Custom options: 2 space indentation and disabled keyword uppercase")
    void testCustomOptions() {
        SqlFormatOptions options = SqlFormatOptions.builder()
                .indentSpaces(2)
                .uppercaseKeywords(false)
                .breakAfterSelectComma(false)
                .build();

        String rawSql = "select a, b from table1 where x = 1";
        String formatted = SqlFormatter.format(rawSql, options);

        assertTrue(formatted.startsWith("select\n  a, b"));
        assertTrue(formatted.contains("from table1"));
    }

    @Test
    @DisplayName("Null or blank SQL returns empty string safely")
    void testEmptyAndNull() {
        assertEquals("", SqlFormatter.format(null));
        assertEquals("", SqlFormatter.format("   "));
    }
}
