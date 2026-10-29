package com.msp.sanitizer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TextSanitizers Facade Unit Tests")
class TextSanitizersTest {

    @Test
    @DisplayName("Delegates PII masking correctly")
    void testPiiDelegation() {
        assertEquals("900101-1******", TextSanitizers.maskRrn("900101-1234567"));
        assertEquals("1234-****-****-5678", TextSanitizers.maskCreditCard("1234-5678-9012-5678"));
        assertEquals("010-****-5678", TextSanitizers.maskPhone("010-1234-5678"));
        assertEquals("te**@test.com", TextSanitizers.maskEmail("test@test.com"));
        assertTrue(TextSanitizers.maskAllPii("Tel: 010-1111-2222, Email: a@b.com").contains("*"));
    }

    @Test
    @DisplayName("Delegates HTML and Log sanitization correctly")
    void testHtmlAndLogDelegation() {
        assertEquals("&lt;b&gt;hi&lt;/b&gt;", TextSanitizers.escapeHtml("<b>hi</b>"));
        assertEquals("hi", TextSanitizers.stripHtml("<b>hi</b>"));
        assertTrue(TextSanitizers.containsXss("<script>alert(1)</script>"));
        assertEquals("a_b", TextSanitizers.sanitizeLog("a\nb"));
        assertTrue(TextSanitizers.containsCrlf("a\r\nb"));
    }
}
