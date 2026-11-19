package com.msp.sqljson;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SqlJsonPredicateBuilderTest {

    @Test
    @DisplayName("JsonPath parses dot and array bracket syntax accurately")
    void parseJsonPath() {
        JsonPath path = JsonPath.parse("$.orders[0].shipping_address.zip");
        assertEquals(4, path.size());
        assertEquals("$.orders[0].shipping_address.zip", path.toDollarPath());
        assertEquals("'{orders,0,shipping_address,zip}'", path.toPostgresPathArrayLiteral());

        JsonPath noDollar = JsonPath.parse("user.roles[2]");
        assertEquals("$.user.roles[2]", noDollar.toDollarPath());
        assertEquals("'{user,roles,2}'", noDollar.toPostgresPathArrayLiteral());

        JsonPath root = JsonPath.parse("$");
        assertTrue(root.isEmpty());
        assertEquals("$", root.toDollarPath());
    }

    @Test
    @DisplayName("PostgreSQL dialect uses ->, ->>, #>, #>>, @>, and ? operators")
    void postgresDialectOperators() {
        SqlJsonPredicateBuilder pg = SqlJsonPredicateBuilder.forDialect(SqlJsonDialect.POSTGRESQL);

        assertEquals("payload -> 'status'", pg.extractJson("payload", JsonPath.of("status")));
        assertEquals("payload ->> 'status'", pg.extractText("payload", JsonPath.of("status")));
        assertEquals("payload -> 0", pg.extractJson("payload", JsonPath.root().index(0)));
        assertEquals("payload ->> 0", pg.extractText("payload", JsonPath.root().index(0)));

        JsonPath nested = JsonPath.parse("$.items[1].sku");
        assertEquals("payload #> '{items,1,sku}'", pg.extractJson("payload", nested));
        assertEquals("payload #>> '{items,1,sku}'", pg.extractText("payload", nested));

        SqlFragment eqStr = pg.eq("orders.payload", nested, "SKU-99");
        assertEquals("orders.payload #>> '{items,1,sku}' = ?", eqStr.sql());
        assertEquals(List.of("SKU-99"), eqStr.parameters());

        SqlFragment gteNum = pg.gte("payload", JsonPath.of("amount"), 1500);
        assertEquals("CAST((payload ->> 'amount') AS NUMERIC) >= ?", gteNum.sql());
        assertEquals(List.of(1500), gteNum.parameters());

        SqlFragment eqBool = pg.eq("payload", JsonPath.of("active"), true);
        assertEquals("CAST((payload ->> 'active') AS BOOLEAN) = ?", eqBool.sql());
        assertEquals(List.of(true), eqBool.parameters());

        SqlFragment hasKey = pg.hasKey("payload", "email");
        assertEquals("payload ? ?", hasKey.sql());
        assertEquals(List.of("email"), hasKey.parameters());

        SqlFragment contains = pg.containsJson("payload", "{\"role\":\"admin\"}");
        assertEquals("payload @> CAST(? AS jsonb)", contains.sql());
        assertEquals(List.of("{\"role\":\"admin\"}"), contains.parameters());

        SqlFragment nestedContains = pg.containsJson("payload", JsonPath.of("tags"), "[\"vip\"]");
        assertEquals("(payload -> 'tags') @> CAST(? AS jsonb)", nestedContains.sql());
        assertEquals(List.of("[\"vip\"]"), nestedContains.parameters());
    }

    @Test
    @DisplayName("MySQL dialect uses JSON_EXTRACT, JSON_UNQUOTE, JSON_CONTAINS, and JSON_CONTAINS_PATH")
    void mysqlDialectFunctions() {
        SqlJsonPredicateBuilder mysql = SqlJsonPredicateBuilder.forDialect(SqlJsonDialect.MYSQL);
        JsonPath path = JsonPath.parse("$.customer.tier");

        assertEquals("JSON_EXTRACT(doc, '$.customer.tier')", mysql.extractJson("doc", path));
        assertEquals("JSON_UNQUOTE(JSON_EXTRACT(doc, '$.customer.tier'))", mysql.extractText("doc", path));

        SqlFragment eq = mysql.eq("doc", path, "GOLD");
        assertEquals("JSON_UNQUOTE(JSON_EXTRACT(doc, '$.customer.tier')) = ?", eq.sql());
        assertEquals(List.of("GOLD"), eq.parameters());

        SqlFragment lt = mysql.lt("doc", JsonPath.of("score"), 90.5);
        assertEquals("JSON_EXTRACT(doc, '$.score') < ?", lt.sql());
        assertEquals(List.of(90.5), lt.parameters());

        SqlFragment exists = mysql.pathExists("doc", path);
        assertEquals("JSON_CONTAINS_PATH(doc, 'one', '$.customer.tier') = 1", exists.sql());
        assertTrue(exists.parameters().isEmpty());

        SqlFragment contains = mysql.containsJson("doc", JsonPath.of("roles"), "\"admin\"");
        assertEquals("JSON_CONTAINS(doc, CAST(? AS JSON), '$.roles') = 1", contains.sql());
        assertEquals(List.of("\"admin\""), contains.parameters());

        SqlFragment numType = mysql.hasType("doc", JsonPath.of("score"), JsonValueType.NUMBER);
        assertEquals("JSON_TYPE(JSON_EXTRACT(doc, '$.score')) IN ('INTEGER', 'DOUBLE', 'DECIMAL')", numType.sql());
    }

    @Test
    @DisplayName("SQLite dialect uses json_extract and json_type")
    void sqliteDialectFunctions() {
        SqlJsonPredicateBuilder sqlite = SqlJsonPredicateBuilder.forDialect(SqlJsonDialect.SQLITE);
        JsonPath path = JsonPath.parse("$.meta.version");

        assertEquals("json_extract(data, '$.meta.version')", sqlite.extractJson("data", path));
        assertEquals("json_extract(data, '$.meta.version')", sqlite.extractText("data", path));

        SqlFragment isNull = sqlite.isNull("data", path);
        assertEquals("(json_type(data, '$.meta.version') IS NULL OR json_type(data, '$.meta.version') = 'null')", isNull.sql());

        SqlFragment exists = sqlite.hasKey("data", "meta");
        assertEquals("json_type(data, '$.meta') IS NOT NULL", exists.sql());

        SqlFragment typeCheck = sqlite.hasType("data", path, JsonValueType.STRING);
        assertEquals("json_type(data, '$.meta.version') = ?", typeCheck.sql());
        assertEquals(List.of("text"), typeCheck.parameters());

        SqlFragment contains = sqlite.containsJson("data", JsonPath.of("flags"), "[1,2]");
        assertEquals("json_type(data, '$.flags') IS NOT NULL AND json_extract(data, '$.flags') = json(?)", contains.sql());
        assertEquals(List.of("[1,2]"), contains.parameters());
    }

    @Test
    @DisplayName("Composite predicates (AND, OR, NOT, IN, LIKE) preserve parameter order")
    void compositePredicatesAndParameterOrder() {
        SqlJsonPredicateBuilder pg = SqlJsonPredicateBuilder.forDialect(SqlJsonDialect.POSTGRESQL);

        SqlFragment p1 = pg.eq("payload", JsonPath.of("country"), "KR");
        SqlFragment p2 = pg.in("payload", JsonPath.of("status"), List.of("PAID", "SHIPPED"));
        SqlFragment p3 = pg.like("payload", JsonPath.parse("$.user.email"), "%@example.com");

        SqlFragment combined = p1.and(p2.or(p3.not()));
        assertEquals(
                "(payload ->> 'country' = ?) AND ((payload ->> 'status' IN (?, ?)) OR (NOT (payload #>> '{user,email}' LIKE ?)))",
                combined.sql()
        );
        assertEquals(List.of("KR", "PAID", "SHIPPED", "%@example.com"), combined.parameters());
    }

    @Test
    @DisplayName("SQL injection attempts in column identifiers and JSON paths are rejected")
    void rejectsSqlInjectionAttempts() {
        SqlJsonPredicateBuilder pg = SqlJsonPredicateBuilder.forDialect(SqlJsonDialect.POSTGRESQL);

        assertThrows(IllegalArgumentException.class, () -> pg.extractText("payload; DROP TABLE users;--", JsonPath.of("a")));
        assertThrows(IllegalArgumentException.class, () -> pg.extractText("payload' OR '1'='1", JsonPath.of("a")));
        assertThrows(IllegalArgumentException.class, () -> pg.extractText("a.b.c.d", JsonPath.of("a")));
        assertThrows(IllegalArgumentException.class, () -> JsonPath.of("a' OR '1'='1"));
        assertThrows(IllegalArgumentException.class, () -> JsonPath.parse("$.user.name;DELETE"));
        assertThrows(IllegalArgumentException.class, () -> JsonPath.parse("$.items[-1]"));
        assertThrows(IllegalArgumentException.class, () -> JsonPath.parse("$.items[abc]"));
        assertThrows(IllegalArgumentException.class, () -> pg.hasKey("payload", "key' ? 'x"));
    }

    @Test
    @DisplayName("Boundary conditions: null eq/ne delegation and empty IN rejection")
    void boundaryConditions() {
        SqlJsonPredicateBuilder mysql = SqlJsonPredicateBuilder.forDialect(SqlJsonDialect.MYSQL);

        SqlFragment eqNull = mysql.eq("doc", JsonPath.of("deletedAt"), null);
        assertTrue(eqNull.sql().contains("IS NULL"));
        assertTrue(eqNull.parameters().isEmpty());

        SqlFragment neNull = mysql.ne("doc", JsonPath.of("deletedAt"), null);
        assertTrue(neNull.sql().contains("IS NOT NULL"));
        assertTrue(neNull.parameters().isEmpty());

        assertThrows(IllegalArgumentException.class, () -> mysql.in("doc", JsonPath.of("id"), List.of()));
        assertThrows(IllegalArgumentException.class, () -> mysql.in("doc", JsonPath.of("id"), java.util.Arrays.asList("a", null)));
    }
}
