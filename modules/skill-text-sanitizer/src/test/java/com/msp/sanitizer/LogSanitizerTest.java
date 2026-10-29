package com.msp.sanitizer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LogSanitizer Unit Tests")
class LogSanitizerTest {

    @Test
    @DisplayName("Neutralizes CRLF injection with underscore replacement")
    void sanitizeDefault() {
        String attack = "admin\r\n[2026-10-30 ERROR] Fake forged log entry\nAnother line";
        String sanitized = LogSanitizer.sanitize(attack);

        assertFalse(sanitized.contains("\r"));
        assertFalse(sanitized.contains("\n"));
        assertEquals("admin_[2026-10-30 ERROR] Fake forged log entry_Another line", sanitized);
    }

    @Test
    @DisplayName("Sanitizes with custom replacement string")
    void sanitizeCustomReplacement() {
        String attack = "User login: john\r\nSTATUS: SUCCESS";
        String sanitized = LogSanitizer.sanitize(attack, " ");
        assertEquals("User login: john STATUS: SUCCESS", sanitized);
    }

    @Test
    @DisplayName("Escapes newlines and tabs to literal strings")
    void escapeLog() {
        String input = "Line1\r\nLine2\tTabbed";
        String escaped = LogSanitizer.escape(input);
        assertEquals("Line1\\r\\nLine2\\tTabbed", escaped);
    }

    @Test
    @DisplayName("Contains CRLF check")
    void containsCrlf() {
        assertTrue(LogSanitizer.containsCrlf("Hello\nWorld"));
        assertTrue(LogSanitizer.containsCrlf("Hello\rWorld"));
        assertFalse(LogSanitizer.containsCrlf("Single line clean log"));
    }

    @Test
    @DisplayName("Handles null and empty strings safely")
    void nullAndEmpty() {
        assertNull(LogSanitizer.sanitize(null));
        assertEquals("", LogSanitizer.sanitize(""));
        assertNull(LogSanitizer.escape(null));
        assertEquals("", LogSanitizer.escape(""));
        assertFalse(LogSanitizer.containsCrlf(null));
    }
}
