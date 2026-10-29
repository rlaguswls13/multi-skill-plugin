package com.msp.sql;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SelectBuilder Unit Tests")
class SelectBuilderTest {

    @Test
    @DisplayName("Basic SELECT * FROM users")
    void basicSelectAll() {
        BoundSql boundSql = SqlBuilders.select().from("users").build();
        assertEquals("SELECT * FROM users", boundSql.sql());
        assertTrue(boundSql.parameters().isEmpty());
    }

    @Test
    @DisplayName("SELECT with projection columns and DISTINCT")
    void selectDistinctWithColumns() {
        BoundSql boundSql = SqlBuilders.selectDistinct("id", "username", "email")
                .from("users")
                .build();
        assertEquals("SELECT DISTINCT id, username, email FROM users", boundSql.sql());
    }

    @Test
    @DisplayName("Joins: INNER, LEFT, RIGHT")
    void joins() {
        BoundSql boundSql = SqlBuilders.select("u.id", "o.total_amount")
                .from("users u")
                .innerJoin("orders o", "o.user_id = u.id")
                .leftJoin("payments p", "p.order_id = o.id")
                .build();

        String expected = "SELECT u.id, o.total_amount FROM users u INNER JOIN orders o ON o.user_id = u.id LEFT JOIN payments p ON p.order_id = o.id";
        assertEquals(expected, boundSql.sql());
    }

    @Nested
    @DisplayName("Dynamic WHERE Filtering")
    class DynamicConditions {

        @Test
        @DisplayName("Only active condition gets WHERE prefix, no dangling AND")
        void dynamicAndIfFirstConditionActive() {
            String nameFilter = null;
            Integer ageFilter = 25;

            BoundSql boundSql = SqlBuilders.select()
                    .from("users")
                    .andIf(nameFilter != null, "username = ?", nameFilter)
                    .andIf(ageFilter != null, "age >= ?", ageFilter)
                    .build();

            assertEquals("SELECT * FROM users WHERE age >= ?", boundSql.sql());
            assertEquals(List.of(25), boundSql.parameters());
        }

        @Test
        @DisplayName("Multiple active conditions combined with AND and OR")
        void multipleConditions() {
            BoundSql boundSql = SqlBuilders.select()
                    .from("users")
                    .where("status = ?", "ACTIVE")
                    .andIf(true, "role = ?", "ADMIN")
                    .orIf(true, "vip_level > ?", 3)
                    .build();

            assertEquals("SELECT * FROM users WHERE status = ? AND role = ? OR vip_level > ?", boundSql.sql());
            assertEquals(List.of("ACTIVE", "ADMIN", 3), boundSql.parameters());
        }

        @Test
        @DisplayName("When all conditions are false, WHERE clause is omitted")
        void allConditionsFalse() {
            BoundSql boundSql = SqlBuilders.select()
                    .from("users")
                    .andIf(false, "a = ?", 1)
                    .andIf(false, "b = ?", 2)
                    .build();

            assertEquals("SELECT * FROM users", boundSql.sql());
            assertTrue(boundSql.parameters().isEmpty());
        }
    }

    @Nested
    @DisplayName("IN Clause Auto-Expansion")
    class InClauseTests {

        @Test
        @DisplayName("Expands non-empty collection into ? placeholders")
        void expandsInClause() {
            List<String> statuses = List.of("PENDING", "APPROVED", "SHIPPED");
            BoundSql boundSql = SqlBuilders.select()
                    .from("orders")
                    .whereIn("status", statuses)
                    .build();

            assertEquals("SELECT * FROM orders WHERE status IN (?, ?, ?)", boundSql.sql());
            assertEquals(statuses, boundSql.parameters());
        }

        @Test
        @DisplayName("Handles empty collection safely with 1 = 0")
        void emptyInClauseExpandsToFalse() {
            BoundSql boundSql = SqlBuilders.select()
                    .from("orders")
                    .whereIn("status", Collections.emptyList())
                    .build();

            assertEquals("SELECT * FROM orders WHERE 1 = 0", boundSql.sql());
            assertTrue(boundSql.parameters().isEmpty());
        }

        @Test
        @DisplayName("NOT IN clause with auto-expansion")
        void notInClause() {
            BoundSql boundSql = SqlBuilders.select()
                    .from("products")
                    .whereNotIn("category_id", List.of(10, 20))
                    .build();

            assertEquals("SELECT * FROM products WHERE category_id NOT IN (?, ?)", boundSql.sql());
            assertEquals(List.of(10, 20), boundSql.parameters());
        }
    }

    @Nested
    @DisplayName("Paging and Ordering")
    class PagingTests {

        @Test
        @DisplayName("Pagination page(1, 10) sets limit 10 offset 0")
        void page1() {
            BoundSql boundSql = SqlBuilders.select()
                    .from("items")
                    .orderBy("id DESC")
                    .page(1, 10)
                    .build();

            assertEquals("SELECT * FROM items ORDER BY id DESC LIMIT ? OFFSET ?", boundSql.sql());
            assertEquals(List.of(10, 0), boundSql.parameters());
        }

        @Test
        @DisplayName("Pagination page(3, 20) sets limit 20 offset 40")
        void page3() {
            BoundSql boundSql = SqlBuilders.select()
                    .from("items")
                    .page(3, 20)
                    .build();

            assertEquals("SELECT * FROM items LIMIT ? OFFSET ?", boundSql.sql());
            assertEquals(List.of(20, 40), boundSql.parameters());
        }
    }

    @Nested
    @DisplayName("Group By and Having")
    class GroupByTests {

        @Test
        @DisplayName("GROUP BY with HAVING condition")
        void groupByHaving() {
            BoundSql boundSql = SqlBuilders.select("department_id", "COUNT(*) as cnt")
                    .from("employees")
                    .groupBy("department_id")
                    .having("COUNT(*) >= ?", 5)
                    .build();

            assertEquals("SELECT department_id, COUNT(*) as cnt FROM employees GROUP BY department_id HAVING COUNT(*) >= ?", boundSql.sql());
            assertEquals(List.of(5), boundSql.parameters());
        }
    }
}
