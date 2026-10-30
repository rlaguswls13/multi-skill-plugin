package com.msp.expr;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ExpressionEvaluatorTest {

    @Test
    @DisplayName("Arithmetic expression evaluation test")
    void testArithmetic() {
        assertEquals(14L, ExpressionEvaluator.evaluate("2 + 3 * 4", Map.of()));
        assertEquals(20L, ExpressionEvaluator.evaluate("(2 + 3) * 4", Map.of()));
        assertEquals(5.5, ExpressionEvaluator.evaluate("11 / 2.0", Map.of()));
    }

    @Test
    @DisplayName("Logical and comparison condition test")
    void testLogicalComparison() {
        Map<String, Object> ctx = Map.of(
                "amount", 60000,
                "grade", "VIP",
                "active", true
        );

        assertTrue(ExpressionEvaluator.evaluateCondition("amount >= 50000 && grade == 'VIP'", ctx));
        assertFalse(ExpressionEvaluator.evaluateCondition("amount > 100000 || grade == 'BASIC'", ctx));
        assertTrue(ExpressionEvaluator.evaluateCondition("(amount > 10000 || grade == 'BASIC') && active", ctx));
    }

    @Test
    @DisplayName("String equality and concatenation test")
    void testStringOperations() {
        Map<String, Object> ctx = Map.of("role", "ADMIN", "user", "Alice");
        assertTrue(ExpressionEvaluator.evaluateCondition("role == 'ADMIN'", ctx));
        assertFalse(ExpressionEvaluator.evaluateCondition("role != 'ADMIN'", ctx));
        assertEquals("Hello Alice", ExpressionEvaluator.evaluate("'Hello ' + user", ctx));
    }
}
