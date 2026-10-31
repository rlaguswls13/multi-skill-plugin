package com.msp.urlcanon;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class UrlCanonicalizerTest {

    @Test
    @DisplayName("Full canonicalization: scheme, host, port, path, query, fragment")
    void testFullCanonicalization() {
        String input = "HTTPS://Example.COM:443/docs//api/./v1/../v2/?utm_source=twitter&version=2&format=json&fbclid=xyz#intro";
        String expected = "https://example.com/docs/api/v2/?format=json&version=2";

        assertEquals(expected, UrlCanonicalizer.canonicalize(input));
    }

    @ParameterizedTest
    @CsvSource({
            "http://example.com:80/test, http://example.com/test",
            "https://example.com:443/test, https://example.com/test",
            "ftp://ftp.example.com:21/files, ftp://ftp.example.com/files",
            "http://example.com:8080/test, http://example.com:8080/test",
            "https://example.com:8443/test, https://example.com:8443/test"
    })
    @DisplayName("Default port removal vs non-default port preservation")
    void testPortNormalization(String input, String expected) {
        assertEquals(expected, UrlCanonicalizer.canonicalize(input));
    }

    @Test
    @DisplayName("Host and scheme lowercasing with root slash addition")
    void testHostAndSchemeLowercasing() {
        assertEquals("https://my-domain.org/", UrlCanonicalizer.canonicalize("HTTPS://MY-DOMAIN.ORG"));
        assertEquals("http://test.com/path", UrlCanonicalizer.canonicalize("HTTP://TEST.COM/path"));
    }

    @Test
    @DisplayName("Path normalization: duplicate slashes and dot segment resolution")
    void testNormalizePath() {
        String url = "https://example.com/a//b/./c/../d/";
        assertEquals("https://example.com/a/b/d/", UrlCanonicalizer.normalizePath(url));

        String urlWithQuery = "https://example.com//items///123?ref=test#top";
        assertEquals("https://example.com/items/123?ref=test#top", UrlCanonicalizer.normalizePath(urlWithQuery));
    }

    @Test
    @DisplayName("removeTrackingParams removes utm, social, and ad tracking tokens")
    void testRemoveTrackingParams() {
        String url = "https://example.com/shop?product=keyboard&utm_source=fb&fbclid=12345&gclid=67890&_ga=GA1.2&color=black";
        String cleaned = UrlCanonicalizer.removeTrackingParams(url);
        assertEquals("https://example.com/shop?product=keyboard&color=black", cleaned);

        // All query params removed
        String onlyTracking = "https://example.com/page?utm_medium=email&utm_campaign=winter_sale";
        assertEquals("https://example.com/page", UrlCanonicalizer.removeTrackingParams(onlyTracking));

        // URL without query remains untouched
        String noQuery = "https://example.com/about";
        assertEquals(noQuery, UrlCanonicalizer.removeTrackingParams(noQuery));
    }

    @Test
    @DisplayName("sortQueryParams sorts alphabetically while preserving other URL parts")
    void testSortQueryParams() {
        String url = "https://example.com/search?z=3&a=1&m=2#section";
        String sorted = UrlCanonicalizer.sortQueryParams(url);
        assertEquals("https://example.com/search?a=1&m=2&z=3#section", sorted);
    }

    @Test
    @DisplayName("Custom CanonicalizeOptions: extra tracking params and fragment preservation")
    void testCustomOptions() {
        CanonicalizeOptions options = CanonicalizeOptions.builder()
                .stripFragment(false)
                .addExtraTrackingParam("custom_tracker")
                .build();

        String url = "https://example.com/?b=2&custom_tracker=foo&a=1#my-fragment";
        String canonical = UrlCanonicalizer.canonicalize(url, options);

        assertEquals("https://example.com/?a=1&b=2#my-fragment", canonical);
    }

    @Test
    @DisplayName("Relative URLs and path-only canonicalization")
    void testRelativeUrl() {
        String relative = "/api//v1/./users/../products/?b=2&a=1";
        assertEquals("/api/v1/products/?a=1&b=2", UrlCanonicalizer.canonicalize(relative));
    }

    @Test
    @DisplayName("Null input validation")
    void testNullValidation() {
        assertThrows(NullPointerException.class, () -> UrlCanonicalizer.canonicalize(null));
        assertThrows(NullPointerException.class, () -> UrlCanonicalizer.removeTrackingParams(null));
        assertThrows(NullPointerException.class, () -> UrlCanonicalizer.normalizePath(null));
        assertThrows(NullPointerException.class, () -> UrlCanonicalizer.sortQueryParams(null));
    }
}
