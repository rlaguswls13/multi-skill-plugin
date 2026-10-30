package com.msp.uritemplate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class UriTemplateMatcherTest {

    @Test
    @DisplayName("Exact path matching without variables")
    void testExactPathMatch() {
        UriTemplateMatcher matcher = UriTemplateMatcher.of("/api/v1/health");

        assertTrue(matcher.matches("/api/v1/health"));
        assertFalse(matcher.matches("/api/v1/status"));
        assertFalse(matcher.matches("/api/v1/health/check"));

        Optional<Map<String, String>> vars = matcher.match("/api/v1/health");
        assertTrue(vars.isPresent());
        assertTrue(vars.get().isEmpty());
    }

    @Test
    @DisplayName("Single path variable extraction")
    void testSingleVariableExtraction() {
        String template = "/api/users/{userId}";
        assertTrue(UriTemplateMatcher.matches(template, "/api/users/42"));

        Optional<Map<String, String>> match = UriTemplateMatcher.match(template, "/api/users/42");
        assertTrue(match.isPresent());
        assertEquals("42", match.get().get("userId"));

        // Single segment variable should not match slash
        assertFalse(UriTemplateMatcher.matches(template, "/api/users/42/details"));
        assertFalse(UriTemplateMatcher.matches(template, "/api/users/"));
    }

    @Test
    @DisplayName("Multiple path variables extraction")
    void testMultipleVariablesExtraction() {
        String template = "/api/users/{userId}/orders/{orderId}";
        Optional<Map<String, String>> match = UriTemplateMatcher.match(template, "/api/users/user-101/orders/ord-909");

        assertTrue(match.isPresent());
        assertEquals(2, match.get().size());
        assertEquals("user-101", match.get().get("userId"));
        assertEquals("ord-909", match.get().get("orderId"));
    }

    @Test
    @DisplayName("Wildcard path variables capturing multiple segments")
    void testWildcardVariables() {
        // RFC 6570 style {path:*}
        String template1 = "/files/{path:*}";
        Optional<Map<String, String>> match1 = UriTemplateMatcher.match(template1, "/files/uploads/2026/10/report.pdf");
        assertTrue(match1.isPresent());
        assertEquals("uploads/2026/10/report.pdf", match1.get().get("path"));

        // Spring style {*path}
        String template2 = "/docs/{*path}";
        Optional<Map<String, String>> match2 = UriTemplateMatcher.match(template2, "/docs/v2/architecture/overview.md");
        assertTrue(match2.isPresent());
        assertEquals("v2/architecture/overview.md", match2.get().get("path"));

        // Trailing wildcard {path*}
        String template3 = "/assets/{path*}";
        Optional<Map<String, String>> match3 = UriTemplateMatcher.match(template3, "/assets/css/style.min.css");
        assertTrue(match3.isPresent());
        assertEquals("css/style.min.css", match3.get().get("path"));
    }

    @Test
    @DisplayName("Handling query strings, fragments, and full URLs")
    void testUrlNormalization() {
        String template = "/api/users/{userId}";

        Optional<Map<String, String>> matchQuery = UriTemplateMatcher.match(template, "/api/users/john?fields=name,age");
        assertTrue(matchQuery.isPresent());
        assertEquals("john", matchQuery.get().get("userId"));

        Optional<Map<String, String>> matchFrag = UriTemplateMatcher.match(template, "/api/users/john#profile");
        assertTrue(matchFrag.isPresent());
        assertEquals("john", matchFrag.get().get("userId"));

        Optional<Map<String, String>> matchFullUrl = UriTemplateMatcher.match(template, "https://example.com/api/users/sarah?tab=1");
        assertTrue(matchFullUrl.isPresent());
        assertEquals("sarah", matchFullUrl.get().get("userId"));
    }

    @Test
    @DisplayName("Literal wildcards * and ** in template")
    void testLiteralWildcards() {
        String singleWildcard = "/api/*/users/{id}";
        assertTrue(UriTemplateMatcher.matches(singleWildcard, "/api/v1/users/7"));
        assertTrue(UriTemplateMatcher.matches(singleWildcard, "/api/v2/users/7"));
        assertFalse(UriTemplateMatcher.matches(singleWildcard, "/api/v1/sub/users/7"));

        String doubleWildcard = "/static/**";
        assertTrue(UriTemplateMatcher.matches(doubleWildcard, "/static/bundle.js"));
        assertTrue(UriTemplateMatcher.matches(doubleWildcard, "/static/sub/dir/img.png"));
        assertFalse(UriTemplateMatcher.matches(doubleWildcard, "/dynamic/bundle.js"));
    }

    @Test
    @DisplayName("Template expansion with variables")
    void testTemplateExpansion() {
        String template = "/api/tenants/{tenantId}/users/{userId}/roles/{roleId}";
        Map<String, Object> vars = Map.of(
                "tenantId", "tenant-alpha",
                "userId", 1001,
                "roleId", "ADMIN"
        );

        String expanded = UriTemplateMatcher.expand(template, vars);
        assertEquals("/api/tenants/tenant-alpha/users/1001/roles/ADMIN", expanded);
    }

    @Test
    @DisplayName("Template expansion with missing variable throws IllegalArgumentException")
    void testTemplateExpansionMissingVariable() {
        String template = "/api/users/{userId}/orders/{orderId}";
        Map<String, Object> vars = Map.of("userId", "123");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                UriTemplateMatcher.expand(template, vars)
        );
        assertTrue(ex.getMessage().contains("orderId"));
    }

    @Test
    @DisplayName("Null handling and edge cases")
    void testEdgeCases() {
        assertFalse(UriTemplateMatcher.matches("/api/test", null));
        assertTrue(UriTemplateMatcher.match("/api/test", null).isEmpty());

        // Template metadata
        UriTemplateMatcher matcher = new UriTemplateMatcher("/api/{org}/{repo}/pulls/{number}");
        assertEquals("/api/{org}/{repo}/pulls/{number}", matcher.getTemplate());
        assertEquals(3, matcher.getVariableNames().size());
        assertEquals("org", matcher.getVariableNames().get(0));
        assertEquals("repo", matcher.getVariableNames().get(1));
        assertEquals("number", matcher.getVariableNames().get(2));
    }
}
