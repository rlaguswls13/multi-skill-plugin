package com.msp.etag;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EtagGeneratorTest {

    @Test
    @DisplayName("Strong ETag generation generates consistent SHA-256 header")
    void testStrongEtag() {
        String content = "Hello World ETag!";
        Etag etag1 = EtagGenerator.generateStrong(content);
        Etag etag2 = EtagGenerator.generateStrong(content);

        assertNotNull(etag1);
        assertFalse(etag1.isWeak());
        assertEquals(etag1.tag(), etag2.tag());
        assertTrue(etag1.toHeaderValue().startsWith("\""));
        assertTrue(etag1.toHeaderValue().endsWith("\""));
        assertEquals(64, etag1.tag().length()); // SHA-256 hex length
    }

    @Test
    @DisplayName("Weak ETag generation prefixes with W/")
    void testWeakEtag() {
        long length = 1024L;
        long lastModified = 1700000000000L;

        Etag weakEtag = EtagGenerator.generateWeak(length, lastModified);
        assertTrue(weakEtag.isWeak());
        assertTrue(weakEtag.toHeaderValue().startsWith("W/\""));
        assertTrue(weakEtag.toHeaderValue().endsWith("\""));
    }

    @Test
    @DisplayName("If-None-Match header matching handles exact, multiple, weak, and wildcard tokens")
    void testEtagMatcher() {
        Etag current = EtagGenerator.generateStrong("Resource Content");
        String headerValue = current.toHeaderValue();

        // Exact match
        assertTrue(EtagMatcher.matchesIfNoneMatch(headerValue, current));

        // Multiple ETags in header
        String multiHeader = "\"dummy-tag\", " + headerValue + ", \"other-tag\"";
        assertTrue(EtagMatcher.matchesIfNoneMatch(multiHeader, current));

        // Wildcard match
        assertTrue(EtagMatcher.matchesIfNoneMatch("*", current));

        // Weak tag in header matching current strong tag (weak comparison)
        String weakHeader = "W/" + headerValue;
        assertTrue(EtagMatcher.matchesIfNoneMatch(weakHeader, current));

        // Mismatched tag
        assertFalse(EtagMatcher.matchesIfNoneMatch("\"completely-different-tag\"", current));

        // Empty / Null header
        assertFalse(EtagMatcher.matchesIfNoneMatch(null, current));
        assertFalse(EtagMatcher.matchesIfNoneMatch("", current));
    }
}
