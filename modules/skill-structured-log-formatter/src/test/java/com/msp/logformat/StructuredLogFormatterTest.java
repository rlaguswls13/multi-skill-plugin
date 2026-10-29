package com.msp.logformat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class StructuredLogFormatterTest {

    @Test
    @DisplayName("Compact formatter outputs single-line JSON with standard attributes")
    void testCompactFormat() {
        StructuredLogFormatter formatter = StructuredLogFormatter.compact();
        Instant now = Instant.parse("2026-10-30T10:00:00Z");

        LogEvent event = LogEvent.builder()
                .timestamp(now)
                .level(LogLevel.INFO)
                .logger("com.msp.service.OrderService")
                .thread("main")
                .message("Order processed successfully: orderId=12345")
                .build();

        String json = formatter.format(event);

        assertFalse(json.contains("\n"));
        assertTrue(json.contains("\"@timestamp\": \"2026-10-30T10:00:00Z\""));
        assertTrue(json.contains("\"level\": \"INFO\""));
        assertTrue(json.contains("\"logger\": \"com.msp.service.OrderService\""));
        assertTrue(json.contains("\"thread\": \"main\""));
        assertTrue(json.contains("\"message\": \"Order processed successfully: orderId=12345\""));
    }

    @Test
    @DisplayName("Context map serialization handles nested structures, lists, and numbers")
    void testContextSerialization() {
        StructuredLogFormatter formatter = StructuredLogFormatter.compact();

        Map<String, Object> context = Map.of(
                "userId", 1001,
                "isActive", true,
                "tags", List.of("vip", "beta"),
                "meta", Map.of("region", "ap-northeast-2")
        );

        String json = formatter.format("WARN", "com.msp.Auth", "Access attempt", context);

        assertTrue(json.contains("\"context\":"));
        assertTrue(json.contains("\"userId\": 1001"));
        assertTrue(json.contains("\"isActive\": true"));
        assertTrue(json.contains("\"tags\": [\"vip\", \"beta\"]") || json.contains("\"tags\": [\"beta\", \"vip\"]") || json.contains("\"vip\""));
        assertTrue(json.contains("\"region\": \"ap-northeast-2\""));
    }

    @Test
    @DisplayName("Error serialization includes type, message, stackTrace array, and cause")
    void testErrorSerialization() {
        StructuredLogFormatter formatter = StructuredLogFormatter.pretty();

        Exception cause = new IOException("Connection reset by peer");
        Exception ex = new IllegalStateException("Database failed", cause);

        String json = formatter.format("ERROR", "com.msp.Db", "DB Query failed", ex);

        assertTrue(json.contains("\"error\":"));
        assertTrue(json.contains("\"type\": \"java.lang.IllegalStateException\""));
        assertTrue(json.contains("\"message\": \"Database failed\""));
        assertTrue(json.contains("\"stackTrace\": ["));
        assertTrue(json.contains("\"causedBy\":"));
        assertTrue(json.contains("\"type\": \"java.io.IOException\""));
        assertTrue(json.contains("\"message\": \"Connection reset by peer\""));
    }

    @Test
    @DisplayName("String escaping handles quotes, backslashes, and control characters")
    void testJsonEscaping() {
        StructuredLogFormatter formatter = StructuredLogFormatter.compact();

        String rawMessage = "Line 1\nLine 2\tTabbed \"Quotes\" and \\backslashes\\";
        String json = formatter.format("DEBUG", "test", rawMessage);

        assertTrue(json.contains("Line 1\\nLine 2\\tTabbed \\\"Quotes\\\" and \\\\backslashes\\\\"));
    }

    @Test
    @DisplayName("LogLevel parsing is case-insensitive with safe fallback")
    void testLogLevelOf() {
        assertEquals(LogLevel.DEBUG, LogLevel.of("debug"));
        assertEquals(LogLevel.WARN, LogLevel.of("WARN"));
        assertEquals(LogLevel.INFO, LogLevel.of("unknown_level"));
        assertEquals(LogLevel.INFO, LogLevel.of(null));
    }
}
