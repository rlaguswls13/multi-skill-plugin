package com.msp.ddl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class TableDdlGeneratorTest {

    record UserAccount(
            Long id,
            String username,
            String postContent,
            String bioDescription,
            boolean active,
            BigDecimal balance,
            Instant registeredAt,
            LocalDate birthDate
    ) {}

    static class OrderItem {
        private Long orderId;
        private String itemName;
        private int quantity;
        private double unitPrice;
    }

    static class EmptyClass {}

    @Test
    @DisplayName("Generate PostgreSQL DDL for record")
    void testPostgresRecordDdl() {
        String ddl = TableDdlGenerator.generateDdl(UserAccount.class, SqlDialect.POSTGRESQL);

        assertTrue(ddl.startsWith("CREATE TABLE user_account ("));
        assertTrue(ddl.contains("id BIGINT PRIMARY KEY,"));
        assertTrue(ddl.contains("username VARCHAR(255),"));
        assertTrue(ddl.contains("post_content TEXT,"));
        assertTrue(ddl.contains("bio_description TEXT,"));
        assertTrue(ddl.contains("active BOOLEAN,"));
        assertTrue(ddl.contains("balance DECIMAL(19, 4),"));
        assertTrue(ddl.contains("registered_at TIMESTAMP,"));
        assertTrue(ddl.contains("birth_date DATE\n);") || ddl.contains("birth_date DATE"));
        assertTrue(ddl.endsWith(");"));
    }

    @Test
    @DisplayName("Generate MySQL DDL with TINYINT(1) boolean")
    void testMySqlRecordDdl() {
        String ddl = TableDdlGenerator.generateDdl(UserAccount.class, SqlDialect.MYSQL);

        assertTrue(ddl.startsWith("CREATE TABLE user_account ("));
        assertTrue(ddl.contains("id BIGINT PRIMARY KEY,"));
        assertTrue(ddl.contains("active TINYINT(1),"));
    }

    @Test
    @DisplayName("Generate DDL for regular class with custom ID field")
    void testRegularClassDdl() {
        String ddl = TableDdlGenerator.generateDdl(OrderItem.class, SqlDialect.H2);

        assertTrue(ddl.startsWith("CREATE TABLE order_item ("));
        assertTrue(ddl.contains("order_id BIGINT PRIMARY KEY,"));
        assertTrue(ddl.contains("item_name VARCHAR(255),"));
        assertTrue(ddl.contains("quantity INTEGER,"));
        assertTrue(ddl.contains("unit_price DOUBLE PRECISION\n);"));
    }

    @Test
    @DisplayName("Snake case conversion utility")
    void testSnakeCase() {
        assertEquals("user_profile", TableDdlGenerator.toSnakeCase("UserProfile"));
        assertEquals("user_account_id", TableDdlGenerator.toSnakeCase("userAccountId"));
        assertEquals("created_at", TableDdlGenerator.toSnakeCase("createdAt"));
        assertEquals("id", TableDdlGenerator.toSnakeCase("id"));
        assertNull(TableDdlGenerator.toSnakeCase(null));
        assertEquals("", TableDdlGenerator.toSnakeCase(""));
    }

    @Test
    @DisplayName("Throws exception on invalid class or empty class")
    void testEmptyClassThrows() {
        assertThrows(IllegalArgumentException.class, () -> TableDdlGenerator.generateDdl(EmptyClass.class));
        assertThrows(NullPointerException.class, () -> TableDdlGenerator.generateDdl(null));
        assertThrows(NullPointerException.class, () -> TableDdlGenerator.generateDdl(UserAccount.class, null));
    }
}
