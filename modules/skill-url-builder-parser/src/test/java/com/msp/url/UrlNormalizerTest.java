package com.msp.url;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UrlNormalizerTest {

    @Test
    @DisplayName("Lowercase scheme and host")
    void lowercaseSchemeAndHost() {
        String normalized = UrlNormalizer.normalize("HTTP://EXAMPLE.COM/Path");
        assertEquals("http://example.com/Path", normalized);
    }

    @Test
    @DisplayName("Remove default ports")
    void removeDefaultPorts() {
        assertEquals("http://example.com/foo", UrlNormalizer.normalize("http://example.com:80/foo"));
        assertEquals("https://example.com/bar", UrlNormalizer.normalize("https://example.com:443/bar"));
        assertEquals("ftp://ftp.example.com/pub", UrlNormalizer.normalize("ftp://ftp.example.com:21/pub"));

        // Non-default port preserved
        assertEquals("http://example.com:8080/foo", UrlNormalizer.normalize("http://example.com:8080/foo"));
    }

    @Test
    @DisplayName("Resolve dot segments in path")
    void resolveDotSegments() {
        assertEquals("/a/c", UrlNormalizer.normalizePath("/a/b/../c"));
        assertEquals("/a/b", UrlNormalizer.normalizePath("/a/./b"));
        assertEquals("/c", UrlNormalizer.normalizePath("/a/b/../../c"));
        assertEquals("/a/b", UrlNormalizer.normalizePath("//a///b"));
    }

    @Test
    @DisplayName("Sort query parameters")
    void sortQueryParams() {
        String input = "https://example.com/api?z=3&a=1&m=2";
        String normalized = UrlNormalizer.normalize(input);
        assertEquals("https://example.com/api?a=1&m=2&z=3", normalized);
    }

    @Test
    @DisplayName("Unescape unreserved percent-encoded characters")
    void unescapeUnreserved() {
        // %7E is '~', %2E is '.'
        String input = "http://example.com/%7Euser/%2E%2E/profile";
        String normalized = UrlNormalizer.normalize(input);
        assertEquals("http://example.com/profile", normalized);
    }

    @Test
    @DisplayName("Null and blank input")
    void nullAndBlank() {
        assertNull(UrlNormalizer.normalize(null));
        assertEquals("", UrlNormalizer.normalize(""));
    }
}
