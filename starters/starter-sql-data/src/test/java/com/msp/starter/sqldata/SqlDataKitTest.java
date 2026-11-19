package com.msp.starter.sqldata;

import com.msp.shardrouter.ShardRoute;
import com.msp.shardrouter.ShardRouter;
import com.msp.sql.BoundSql;
import com.msp.sqlinjection.SqlInjectionDetection;
import com.msp.sqlinjection.SqlInjectionException;
import com.msp.sqljson.JsonPath;
import com.msp.sqljson.SqlFragment;
import com.msp.sqljson.SqlJsonDialect;
import com.msp.sqlsanitizer.SqlDialect;
import com.msp.sqlseek.NavigationDirection;
import com.msp.sqlseek.SeekQuerySegment;
import com.msp.sqlseek.SortSpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SqlDataKitTest {

    @Test
    @DisplayName("1. Fluent parameterized SQL builder (SELECT / INSERT / UPDATE / DELETE)")
    void testSqlBuilder() {
        BoundSql bound = SqlDataKit.select("id", "email")
                .from("users")
                .where("status = ?", "ACTIVE")
                .andIn("role", List.of("ADMIN", "EDITOR"))
                .build();

        assertTrue(bound.sql().contains("SELECT id, email FROM users WHERE status = ? AND role IN (?, ?)"));
        assertEquals(List.of("ACTIVE", "ADMIN", "EDITOR"), bound.parameters());
    }

    @Test
    @DisplayName("2. SQL identifier validation and dialect-aware quoting")
    void testIdentifierSanitizer() {
        assertTrue(SqlDataKit.isValidIdentifier("order_items"));
        assertFalse(SqlDataKit.isValidIdentifier("users; DROP TABLE users--"));
        assertTrue(SqlDataKit.isReservedKeyword("select"));

        assertEquals("`user_name`", SqlDataKit.sanitizeIdentifier("user_name", SqlDialect.MYSQL));
        assertEquals("\"public\".\"orders\"", SqlDataKit.sanitizeQualifiedIdentifier("public.orders", SqlDialect.POSTGRESQL));
        assertThrows(IllegalArgumentException.class, () ->
                SqlDataKit.sanitizeIdentifier("users; DROP TABLE users", SqlDialect.ANSI_STANDARD));
    }

    @Test
    @DisplayName("3. SQL injection pattern detection and assertion")
    void testSqlInjectionDetector() {
        assertTrue(SqlDataKit.isSqlSafe("normal-search-keyword-2026"));
        assertDoesNotThrow(() -> SqlDataKit.assertSqlSafe("john.doe@example.com"));

        SqlInjectionDetection det = SqlDataKit.detectSqlInjection("' OR 1=1 --");
        assertTrue(det.detected());
        assertNotNull(det.ruleName());

        assertFalse(SqlDataKit.isSqlSafe("1 UNION SELECT username, password FROM users"));
        assertThrows(SqlInjectionException.class, () -> SqlDataKit.assertSqlSafe("1; DROP TABLE orders"));
    }

    @Test
    @DisplayName("4. Multi-column keyset / seek pagination builder")
    void testKeysetSeekPagination() {
        SeekQuerySegment segment = SqlDataKit.buildKeysetSeek(
                List.of(SortSpec.desc("created_at"), SortSpec.desc("id")),
                List.of("2026-05-01T12:00:00Z", 9001L),
                25,
                NavigationDirection.NEXT_PAGE
        );

        assertTrue(segment.hasWherePredicate());
        assertEquals("(created_at < ? OR (created_at = ? AND id < ?))", segment.wherePredicate());
        assertEquals("ORDER BY created_at DESC, id DESC", segment.orderByClause());
        assertEquals("LIMIT 25", segment.limitClause());
        assertEquals(List.of("2026-05-01T12:00:00Z", "2026-05-01T12:00:00Z", 9001L), segment.bindParameters());
    }

    @Test
    @DisplayName("5. Database & table sharding router (modulo & consistent hash)")
    void testShardingRouter() {
        ShardRoute route = SqlDataKit.routeModulo("orders", 4, 4, 17L);
        assertEquals(1, route.globalShardIndex());
        assertEquals("ds_00", route.dataSource());
        assertEquals("orders_001", route.physicalTable());
        assertEquals("ds_00.orders_001", route.qualifiedName());

        ShardRouter consistent = SqlDataKit.consistentHashRouter("orders", 2, 4);
        ShardRoute r1 = consistent.route("tenant-alpha");
        ShardRoute r2 = consistent.route("tenant-alpha");
        assertEquals(r1, r2);
        assertEquals(8, consistent.allRoutes().size());
    }

    @Test
    @DisplayName("6. Multi-dialect SQL JSON path predicate builder")
    void testJsonPathPredicateBuilder() {
        SqlFragment pgEq = SqlDataKit.jsonEquals(SqlJsonDialect.POSTGRESQL, "payload", "$.user.city", "Seoul");
        assertEquals("payload #>> '{user,city}' = ?", pgEq.sql());
        assertEquals(List.of("Seoul"), pgEq.parameters());

        SqlFragment mysqlGte = SqlDataKit.jsonPredicates(SqlJsonDialect.MYSQL)
                .gte("payload", SqlDataKit.jsonPath("$.items[0].price"), 100);
        assertEquals("JSON_EXTRACT(payload, '$.items[0].price') >= ?", mysqlGte.sql());
        assertEquals(List.of(100), mysqlGte.parameters());

        JsonPath path = SqlDataKit.jsonPath("$.meta.verified");
        SqlFragment sqliteEq = SqlDataKit.jsonPredicates(SqlJsonDialect.SQLITE).eq("payload", path, true);
        assertEquals("json_extract(payload, '$.meta.verified') = ?", sqliteEq.sql());
        assertEquals(List.of(true), sqliteEq.parameters());
    }
}
