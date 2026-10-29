package com.msp.sql;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Insert, Update, Delete Builders Unit Tests")
class MutationBuildersTest {

    @Test
    @DisplayName("INSERT statement with chain and map values")
    void testInsertBuilder() {
        BoundSql boundSql = SqlBuilders.insertInto("users")
                .value("name", "Alice")
                .value("email", "alice@example.com")
                .valueIf(false, "phone", "010-0000-0000")
                .valueIf(true, "age", 30)
                .build();

        assertEquals("INSERT INTO users (name, email, age) VALUES (?, ?, ?)", boundSql.sql());
        assertEquals(List.of("Alice", "alice@example.com", 30), boundSql.parameters());
    }

    @Test
    @DisplayName("INSERT from Map")
    void testInsertFromMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("col1", 100);
        map.put("col2", "foo");

        BoundSql boundSql = SqlBuilders.insertInto("tbl").values(map).build();
        assertEquals("INSERT INTO tbl (col1, col2) VALUES (?, ?)", boundSql.sql());
        assertEquals(List.of(100, "foo"), boundSql.parameters());
    }

    @Test
    @DisplayName("UPDATE statement with set, setIf, setSql and where")
    void testUpdateBuilder() {
        BoundSql boundSql = SqlBuilders.update("users")
                .set("name", "Bob")
                .setIf(true, "status", "ACTIVE")
                .setIf(false, "points", 999)
                .setSql("updated_at = NOW()")
                .where("id = ?", 42)
                .andIn("role", List.of("USER", "MEMBER"))
                .build();

        String expected = "UPDATE users SET name = ?, status = ?, updated_at = NOW() WHERE id = ? AND role IN (?, ?)";
        assertEquals(expected, boundSql.sql());
        assertEquals(List.of("Bob", "ACTIVE", 42, "USER", "MEMBER"), boundSql.parameters());
    }

    @Test
    @DisplayName("DELETE statement with where and andIf")
    void testDeleteBuilder() {
        BoundSql boundSql = SqlBuilders.deleteFrom("logs")
                .where("created_at < ?", "2026-01-01")
                .andIf(true, "level = ?", "DEBUG")
                .build();

        assertEquals("DELETE FROM logs WHERE created_at < ? AND level = ?", boundSql.sql());
        assertEquals(List.of("2026-01-01", "DEBUG"), boundSql.parameters());
    }

    @Test
    @DisplayName("Throws exception if table name is missing")
    void tableMissingThrows() {
        assertThrows(IllegalStateException.class, () -> new SelectBuilder().build());
        assertThrows(IllegalStateException.class, () -> new InsertBuilder().value("a", 1).build());
        assertThrows(IllegalStateException.class, () -> new UpdateBuilder().set("a", 1).build());
        assertThrows(IllegalStateException.class, () -> new DeleteBuilder().build());
    }
}
