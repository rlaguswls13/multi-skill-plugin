package com.msp.sql;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BoundSql and SqlBinder Unit Tests")
class BoundSqlAndBinderTest {

    @Test
    @DisplayName("BoundSql record immutability and parameterCount")
    void testBoundSqlProperties() {
        List<Object> mutable = new ArrayList<>(List.of("one", 2));
        BoundSql boundSql = new BoundSql("SELECT ?", mutable);

        assertEquals(2, boundSql.parameterCount());
        mutable.add("three"); // mutating source list
        assertEquals(2, boundSql.parameters().size(), "BoundSql parameters must be defensively copied");
    }

    @Test
    @DisplayName("SqlBinder binds positional parameters to PreparedStatement (1-based index)")
    void testPreparedStatementBinding() throws SQLException {
        List<String> capturedCalls = new ArrayList<>();

        PreparedStatement psMock = (PreparedStatement) Proxy.newProxyInstance(
                PreparedStatement.class.getClassLoader(),
                new Class<?>[]{PreparedStatement.class},
                (proxy, method, args) -> {
                    if ("setObject".equals(method.getName())) {
                        capturedCalls.add(args[0] + "=" + args[1]);
                        return null;
                    }
                    return null;
                }
        );

        BoundSql boundSql = new BoundSql("SELECT * FROM tbl WHERE a = ? AND b = ?", List.of("alpha", 42));
        boundSql.bindTo(psMock);

        assertEquals(2, capturedCalls.size());
        assertEquals("1=alpha", capturedCalls.get(0));
        assertEquals("2=42", capturedCalls.get(1));
    }

    @Test
    @DisplayName("SqlBuilder alias entry point works identically to SqlBuilders")
    void testSqlBuilderAlias() {
        BoundSql b1 = SqlBuilder.select("id").from("users").build();
        BoundSql b2 = SqlBuilders.select("id").from("users").build();
        assertEquals(b1.sql(), b2.sql());
    }
}
