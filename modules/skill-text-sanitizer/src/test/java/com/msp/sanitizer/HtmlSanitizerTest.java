package com.msp.sanitizer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("HtmlSanitizer Unit Tests")
class HtmlSanitizerTest {

    @Nested
    @DisplayName("HTML Entity Escaping")
    class EscapeTests {

        @Test
        @DisplayName("Escapes standard HTML characters")
        void escapeHtml() {
            String raw = "<div class=\"box\" id='1'>Tom & Jerry > Mickey < Donald</div>";
            String escaped = HtmlSanitizer.escapeHtml(raw);
            assertEquals("&lt;div class=&quot;box&quot; id=&#39;1&#39;&gt;Tom &amp; Jerry &gt; Mickey &lt; Donald&lt;/div&gt;", escaped);

            String unescaped = HtmlSanitizer.unescapeHtml(escaped);
            assertEquals(raw, unescaped);
        }

        @Test
        @DisplayName("Null and empty inputs")
        void nullOrEmpty() {
            assertNull(HtmlSanitizer.escapeHtml(null));
            assertEquals("", HtmlSanitizer.escapeHtml(""));
            assertNull(HtmlSanitizer.unescapeHtml(null));
            assertEquals("", HtmlSanitizer.unescapeHtml(""));
        }
    }

    @Nested
    @DisplayName("Strip Tags")
    class StripTagTests {

        @Test
        @DisplayName("Strips all HTML tags and removes script body")
        void stripTags() {
            String html = "<h1>Title</h1><p>Hello <b>World</b>!</p><script>alert('xss');</script><!-- comment -->";
            String stripped = HtmlSanitizer.stripTags(html);
            assertEquals("TitleHello World!", stripped.trim());
        }
    }

    @Nested
    @DisplayName("Sanitize XSS")
    class SanitizeTests {

        @Test
        @DisplayName("Removes <script> tags and inner code")
        void stripScriptTag() {
            String malicious = "<p>Safe text</p><script>evilCode();</script>";
            String sanitized = HtmlSanitizer.sanitize(malicious);
            assertEquals("<p>Safe text</p>", sanitized);
        }

        @Test
        @DisplayName("Removes <iframe> and <object> tags")
        void stripIframeAndObject() {
            String html = "<iframe src=\"http://attacker.com\"></iframe><object data=\"bad\"></object><div>Good</div>";
            String sanitized = HtmlSanitizer.sanitize(html);
            assertEquals("<div>Good</div>", sanitized);
        }

        @Test
        @DisplayName("Removes inline event handlers like onload, onerror, onclick")
        void stripInlineEventHandlers() {
            String html = "<img src=\"avatar.png\" onload=\"alert('pwned')\" onerror='stealCookies()'><a href=\"#\" onclick=\"evil()\">Click</a>";
            String sanitized = HtmlSanitizer.sanitize(html);
            assertFalse(sanitized.contains("onload"));
            assertFalse(sanitized.contains("onerror"));
            assertFalse(sanitized.contains("onclick"));
            assertTrue(sanitized.contains("<img src=\"avatar.png\">"));
            assertTrue(sanitized.contains("<a href=\"#\">Click</a>"));
        }

        @Test
        @DisplayName("Removes javascript: pseudo-protocol in href")
        void stripJavascriptProtocol() {
            String html = "<a href=\"javascript:alert('xss')\">Click me</a><a href='JAVASCRIPT:evil()'>Evil</a><a href=\"https://example.com\">Safe</a>";
            String sanitized = HtmlSanitizer.sanitize(html);
            assertFalse(sanitized.contains("javascript:"));
            assertFalse(sanitized.contains("JAVASCRIPT:"));
            assertTrue(sanitized.contains("<a href=\"https://example.com\">Safe</a>"));
        }

        @Test
        @DisplayName("Removes <svg> attack vectors")
        void stripSvg() {
            String html = "<svg><script>alert(1)</script></svg><p>Safe</p>";
            String sanitized = HtmlSanitizer.sanitize(html);
            assertEquals("<p>Safe</p>", sanitized);
        }
    }

    @Nested
    @DisplayName("Detection of Suspicious XSS")
    class DetectionTests {

        @ParameterizedTest
        @ValueSource(strings = {
                "<script>alert(1)</script>",
                "<img src=x onerror=alert(1)>",
                "<iframe src='//evil.com'>",
                "<a href=\"javascript:void(0)\">",
                "<body onload=attack()>",
                "<svg onload=alert(1)>"
        })
        @DisplayName("Identifies common XSS attack vectors")
        void detectXss(String input) {
            assertTrue(HtmlSanitizer.containsXss(input));
        }

        @Test
        @DisplayName("Accepts normal benign text")
        void benignText() {
            assertFalse(HtmlSanitizer.containsXss("Hello, this is just normal plain text."));
            assertFalse(HtmlSanitizer.containsXss("<p>Welcome to our service</p>"));
        }
    }
}
