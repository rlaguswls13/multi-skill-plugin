package com.msp.contentneg;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ContentNegotiatorTest {

    @Test
    @DisplayName("Should parse quality headers with default q=1.0 and parameters")
    void testParseQualityHeader() {
        String header = "text/html, application/xhtml+xml, application/xml;q=0.9, */*;q=0.8;charset=utf-8";
        List<QualityValue<String>> parsed = ContentNegotiator.parseQualityHeader(header);

        assertEquals(4, parsed.size());

        assertEquals("text/html", parsed.get(0).value());
        assertEquals(1.0, parsed.get(0).quality());
        assertTrue(parsed.get(0).parameters().isEmpty());

        assertEquals("application/xhtml+xml", parsed.get(1).value());
        assertEquals(1.0, parsed.get(1).quality());

        assertEquals("application/xml", parsed.get(2).value());
        assertEquals(0.9, parsed.get(2).quality());

        assertEquals("*/*", parsed.get(3).value());
        assertEquals(0.8, parsed.get(3).quality());
        assertEquals("utf-8", parsed.get(3).parameters().get("charset"));
    }

    @Test
    @DisplayName("Should negotiate Content-Type according to quality factor sorting")
    void testNegotiateContentTypeQualitySorting() {
        List<String> supported = List.of("text/html", "application/json", "application/xml");

        // Client prefers application/json with q=0.9 over text/html with q=0.5
        String accept1 = "text/html;q=0.5, application/json;q=0.9";
        assertEquals("application/json", ContentNegotiator.negotiateContentType(accept1, supported));

        // Client prefers text/html (default q=1.0)
        String accept2 = "application/json;q=0.8, text/html";
        assertEquals("text/html", ContentNegotiator.negotiateContentType(accept2, supported));
    }

    @Test
    @DisplayName("Should prefer more specific MIME types when qualities are equal")
    void testNegotiateContentTypeSpecificity() {
        List<String> supported = List.of("text/plain", "application/json");

        // text/* vs application/json (both q=1.0): exact application/json is more specific than text/*
        String accept = "*/*, text/*, application/json";
        assertEquals("application/json", ContentNegotiator.negotiateContentType(accept, supported));

        // Wildcard fallback
        String acceptWildcard = "*/*";
        assertEquals("text/plain", ContentNegotiator.negotiateContentType(acceptWildcard, supported));

        // Subtype wildcard fallback
        String acceptSubWildcard = "text/*";
        assertEquals("text/plain", ContentNegotiator.negotiateContentType(acceptSubWildcard, supported));
    }

    @Test
    @DisplayName("Should return null or fallback properly on unsupported or empty content types")
    void testNegotiateContentTypeUnsupported() {
        List<String> supported = List.of("application/json", "text/plain");

        // Client only accepts image/webp
        assertEquals(null, ContentNegotiator.negotiateContentType("image/webp", supported));

        // Client rejects json with q=0.0
        assertEquals(null, ContentNegotiator.negotiateContentType("application/json;q=0.0", List.of("application/json")));

        // Empty or null header falls back to first supported
        assertEquals("application/json", ContentNegotiator.negotiateContentType(null, supported));
        assertEquals("application/json", ContentNegotiator.negotiateContentType("   ", supported));
    }

    @Test
    @DisplayName("Should negotiate language with exact, subtag, and prefix matching")
    void testNegotiateLanguage() {
        List<String> supported = List.of("en", "ko", "ja");

        // 1. Exact match
        assertEquals("ko", ContentNegotiator.negotiateLanguage("ko, en;q=0.8", supported));

        // 2. Subtag fallback: client requested "en-US", server supports "en"
        assertEquals("en", ContentNegotiator.negotiateLanguage("en-US, ja;q=0.5", supported));

        // 3. Prefix match: client requested "en", server supports "en-GB"
        List<String> supportedRegional = List.of("en-GB", "fr-FR");
        assertEquals("en-GB", ContentNegotiator.negotiateLanguage("en, fr;q=0.5", supportedRegional));

        // 4. Wildcard language '*'
        assertEquals("en", ContentNegotiator.negotiateLanguage("*", supported));

        // 5. Unsupported language
        assertNull(ContentNegotiator.negotiateLanguage("de-DE, it", supported));

        // 6. Null header fallback
        assertEquals("en", ContentNegotiator.negotiateLanguage(null, supported));
    }

    @Test
    @DisplayName("Should negotiate encoding with q-values and wildcards")
    void testNegotiateEncoding() {
        List<String> supported = List.of("gzip", "br", "deflate");

        // Quality sorting: br (q=1.0) preferred over gzip (q=0.7)
        String header = "gzip;q=0.7, br;q=1.0";
        assertEquals("br", ContentNegotiator.negotiateEncoding(header, supported));

        // Wildcard encoding
        assertEquals("gzip", ContentNegotiator.negotiateEncoding("*", supported));

        // Unsupported encoding
        assertNull(ContentNegotiator.negotiateEncoding("compress, zstd", supported));

        // Null header fallback
        assertEquals("gzip", ContentNegotiator.negotiateEncoding(null, supported));
    }
}
