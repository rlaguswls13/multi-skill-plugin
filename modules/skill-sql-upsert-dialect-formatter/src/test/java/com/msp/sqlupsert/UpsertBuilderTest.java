package com.msp.sqlupsert;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class UpsertBuilderTest {

    @Test
    @DisplayName("PostgreSQL: Standard UPSERT with conflict key and column updates")
    void testPostgresUpsert() {
        UpsertStatement stmt = UpsertBuilder.into("users")
                .conflictKeys("email")
                .value("email", "alice@example.com")
                .value("name", "Alice")
                .value("score", 100)
                .updateOnConflict("name", "score")
                .forDialect(DatabaseDialect.POSTGRESQL)
                .build();

        String sql = stmt.sql();
        assertTrue(sql.contains("INSERT INTO users (email, name, score)"));
        assertTrue(sql.contains("VALUES (?, ?, ?)"));
        assertTrue(sql.contains("ON CONFLICT (email) DO UPDATE SET"));
        assertTrue(sql.contains("name = EXCLUDED.name"));
        assertTrue(sql.contains("score = EXCLUDED.score"));
        assertEquals(3, stmt.parameters().size());
        assertEquals("alice@example.com", stmt.parameters().get(0));
        assertEquals("Alice", stmt.parameters().get(1));
        assertEquals(100, stmt.parameters().get(2));
    }

    @Test
    @DisplayName("PostgreSQL: ON CONFLICT DO NOTHING")
    void testPostgresDoNothing() {
        UpsertStatement stmt = UpsertBuilder.into("tags")
                .conflictKeys("name")
                .value("name", "java")
                .doNothingOnConflict()
                .forDialect(DatabaseDialect.POSTGRESQL)
                .build();

        assertTrue(stmt.sql().contains("ON CONFLICT (name) DO NOTHING"));
    }

    @Test
    @DisplayName("SQLite: UPSERT with excluded.column")
    void testSqliteUpsert() {
        UpsertStatement stmt = UpsertBuilder.into("settings")
                .conflictKeys("key")
                .value("key", "theme")
                .value("value", "dark")
                .updateOnConflict("value")
                .forDialect(DatabaseDialect.SQLITE)
                .build();

        String sql = stmt.sql();
        assertTrue(sql.contains("ON CONFLICT (key) DO UPDATE SET"));
        assertTrue(sql.contains("value = excluded.value"));
    }

    @Test
    @DisplayName("MySQL: ON DUPLICATE KEY UPDATE with VALUES(col)")
    void testMySqlUpsert() {
        UpsertStatement stmt = UpsertBuilder.into("users")
                .conflictKeys("id")
                .value("id", 1)
                .value("name", "Bob")
                .value("status", "ACTIVE")
                .updateOnConflict("name", "status")
                .forDialect(DatabaseDialect.MYSQL)
                .build();

        String sql = stmt.sql();
        assertTrue(sql.startsWith("INSERT INTO users (id, name, status)"));
        assertTrue(sql.contains("VALUES (?, ?, ?)"));
        assertTrue(sql.contains("ON DUPLICATE KEY UPDATE"));
        assertTrue(sql.contains("name = VALUES(name)"));
        assertTrue(sql.contains("status = VALUES(status)"));
    }

    @Test
    @DisplayName("MySQL: DO NOTHING becomes INSERT IGNORE")
    void testMySqlDoNothing() {
        UpsertStatement stmt = UpsertBuilder.into("logs")
                .value("event_id", "evt_123")
                .value("message", "User login")
                .doNothingOnConflict()
                .forDialect(DatabaseDialect.MYSQL)
                .build();

        assertTrue(stmt.sql().startsWith("INSERT IGNORE INTO logs"));
    }

    @Test
    @DisplayName("Oracle: MERGE INTO ... USING (SELECT ... FROM DUAL)")
    void testOracleMerge() {
        UpsertStatement stmt = UpsertBuilder.into("accounts")
                .conflictKeys("account_id")
                .value("account_id", "acc_99")
                .value("balance", 5000)
                .updateOnConflict("balance")
                .forDialect(DatabaseDialect.ORACLE)
                .build();

        String sql = stmt.sql();
        assertTrue(sql.startsWith("MERGE INTO accounts t"));
        assertTrue(sql.contains("USING (SELECT ? AS account_id, ? AS balance FROM DUAL) s"));
        assertTrue(sql.contains("ON (t.account_id = s.account_id)"));
        assertTrue(sql.contains("WHEN MATCHED THEN"));
        assertTrue(sql.contains("UPDATE SET"));
        assertTrue(sql.contains("t.balance = s.balance"));
        assertTrue(sql.contains("WHEN NOT MATCHED THEN"));
        assertTrue(sql.contains("INSERT (account_id, balance)"));
        assertTrue(sql.contains("VALUES (s.account_id, s.balance)"));
    }

    @Test
    @DisplayName("SQL Server: MERGE INTO ... USING (VALUES (...)) ending with semicolon")
    void testSqlServerMerge() {
        UpsertStatement stmt = UpsertBuilder.into("inventory")
                .conflictKeys("sku")
                .value("sku", "ITEM-01")
                .value("qty", 50)
                .updateOnConflict("qty")
                .forDialect(DatabaseDialect.SQL_SERVER)
                .build();

        String sql = stmt.sql();
        assertTrue(sql.startsWith("MERGE INTO inventory AS target"));
        assertTrue(sql.contains("USING (VALUES (?, ?)) AS source (sku, qty)"));
        assertTrue(sql.contains("ON (target.sku = source.sku)"));
        assertTrue(sql.contains("UPDATE SET"));
        assertTrue(sql.contains("target.qty = source.qty"));
        assertTrue(sql.endsWith(";"));
    }

    @Test
    @DisplayName("Custom SQL Expression on conflict (e.g. NOW(), counter increment)")
    void testCustomExpression() {
        UpsertStatement stmt = UpsertBuilder.into("page_views")
                .conflictKeys("page_id")
                .value("page_id", "home")
                .value("views", 1)
                .value("last_visited", "2026-11-01")
                .updateCustom("views", "page_views.views + 1")
                .updateCustom("last_visited", "CURRENT_TIMESTAMP")
                .forDialect(DatabaseDialect.POSTGRESQL)
                .build();

        String sql = stmt.sql();
        assertTrue(sql.contains("views = page_views.views + 1"));
        assertTrue(sql.contains("last_visited = CURRENT_TIMESTAMP"));
    }

    @Test
    @DisplayName("Named parameter styles: colon and dollar")
    void testNamedParameters() {
        UpsertStatement colonStmt = UpsertBuilder.into("users")
                .conflictKeys("id")
                .value("id", 10)
                .value("name", "Carol")
                .parameterStyle(ParameterStyle.NAMED_COLON)
                .forDialect(DatabaseDialect.POSTGRESQL)
                .build();

        assertTrue(colonStmt.sql().contains("VALUES (:id, :name)"));

        UpsertStatement dollarStmt = UpsertBuilder.into("users")
                .conflictKeys("id")
                .value("id", 10)
                .value("name", "Carol")
                .parameterStyle(ParameterStyle.NAMED_DOLLAR)
                .forDialect(DatabaseDialect.POSTGRESQL)
                .build();

        assertTrue(dollarStmt.sql().contains("VALUES (${id}, ${name})"));
    }

    @Test
    @DisplayName("Composite conflict keys across tenant and user")
    void testCompositeKeys() {
        UpsertStatement stmt = UpsertBuilder.into("tenant_users")
                .conflictKeys("tenant_id", "user_id")
                .value("tenant_id", "t-1")
                .value("user_id", "u-1")
                .value("role", "ADMIN")
                .forDialect(DatabaseDialect.POSTGRESQL)
                .build();

        assertTrue(stmt.sql().contains("ON CONFLICT (tenant_id, user_id) DO UPDATE SET"));
        assertTrue(stmt.sql().contains("role = EXCLUDED.role"));
    }

    @Test
    @DisplayName("Validation: empty table and missing values throw IllegalArgumentException")
    void testValidation() {
        assertThrows(IllegalArgumentException.class, () -> UpsertBuilder.into(""));
        assertThrows(IllegalArgumentException.class, () -> UpsertBuilder.into("   "));
        assertThrows(IllegalArgumentException.class, () -> UpsertBuilder.into(null));

        UpsertBuilder builder = UpsertBuilder.into("test");
        assertThrows(IllegalStateException.class, builder::build);

        // Conflict keys required for PostgreSQL
        UpsertBuilder noKeyBuilder = UpsertBuilder.into("test").value("col", "val").forDialect(DatabaseDialect.POSTGRESQL);
        assertThrows(IllegalStateException.class, noKeyBuilder::build);
    }
}
