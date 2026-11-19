package com.msp.secheaders;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SecurityHeadersTest {

    @Test
    @DisplayName("Strict preset contains all recommended modern security headers")
    void testStrictPreset() {
        SecurityHeaders headers = SecurityHeadersBuilder.strict();
        Map<String, String> map = headers.toHeadersMap();

        assertTrue(map.containsKey(SecurityHeaders.HEADER_HSTS));
        assertTrue(map.get(SecurityHeaders.HEADER_HSTS).contains("preload"));
        assertTrue(map.containsKey(SecurityHeaders.HEADER_CSP));
        assertEquals("DENY", map.get(SecurityHeaders.HEADER_X_FRAME_OPTIONS));
        assertEquals("nosniff", map.get(SecurityHeaders.HEADER_X_CONTENT_TYPE_OPTIONS));
        assertEquals("strict-origin-when-cross-origin", map.get(SecurityHeaders.HEADER_REFERRER_POLICY));
        assertTrue(map.containsKey(SecurityHeaders.HEADER_PERMISSIONS_POLICY));
        assertEquals("same-origin", map.get(SecurityHeaders.HEADER_COOP));
        assertEquals("require-corp", map.get(SecurityHeaders.HEADER_COEP));
        assertEquals("same-origin", map.get(SecurityHeaders.HEADER_CORP));

        SecurityPolicyValidator.ValidationReport report = SecurityPolicyValidator.validate(headers);
        assertTrue(report.isValid(), "Strict preset should have no validation errors");
        assertEquals(0, report.issuesBySeverity(SecurityPolicyValidator.Severity.ERROR).size());
    }

    @Test
    @DisplayName("API preset provides minimal attack surface for REST/JSON services")
    void testApiPreset() {
        SecurityHeaders headers = SecurityHeadersBuilder.api();
        Map<String, String> map = headers.toHeadersMap();

        assertEquals("DENY", map.get(SecurityHeaders.HEADER_X_FRAME_OPTIONS));
        assertEquals("nosniff", map.get(SecurityHeaders.HEADER_X_CONTENT_TYPE_OPTIONS));
        assertEquals("no-referrer", map.get(SecurityHeaders.HEADER_REFERRER_POLICY));
        assertEquals("default-src 'none'; frame-ancestors 'none'", map.get(SecurityHeaders.HEADER_CSP));
    }

    @Test
    @DisplayName("Fluent CSP builder renders properly formatted directives")
    void testCspBuilder() {
        CspConfig csp = CspConfig.builder()
                .defaultSrc("'self'")
                .scriptSrc("'self'", "https://cdn.example.com", "'nonce-abc123xyz'")
                .styleSrc("'self'", "https://fonts.googleapis.com")
                .fontSrc("'self'", "https://fonts.gstatic.com")
                .imgSrc("'self'", "data:")
                .connectSrc("'self'", "https://api.example.com")
                .frameAncestors("'none'")
                .upgradeInsecureRequests()
                .build();

        String headerValue = csp.toHeaderValue();
        assertTrue(headerValue.contains("default-src 'self'"));
        assertTrue(headerValue.contains("script-src 'self' https://cdn.example.com 'nonce-abc123xyz'"));
        assertTrue(headerValue.contains("upgrade-insecure-requests"));
    }

    @Test
    @DisplayName("Permissions-Policy formats feature directives with allowlists")
    void testPermissionsPolicy() {
        PermissionsPolicyConfig policy = PermissionsPolicyConfig.builder()
                .camera()
                .microphone()
                .geolocation("self", "\"https://geo.example.com\"")
                .fullscreen("*")
                .build();

        String formatted = policy.toHeaderValue();
        assertTrue(formatted.contains("camera=()"));
        assertTrue(formatted.contains("microphone=()"));
        assertTrue(formatted.contains("geolocation=(self \"https://geo.example.com\")"));
        assertTrue(formatted.contains("fullscreen=*"));
    }

    @Test
    @DisplayName("applyTo consumes all headers accurately")
    void testApplyTo() {
        SecurityHeaders headers = SecurityHeaders.builder()
                .hsts(3600, false, false)
                .xFrameOptions(XFrameOptions.SAMEORIGIN)
                .contentTypeOptionsNosniff(true)
                .customHeader("X-Custom-Header", "Secured")
                .build();

        Map<String, String> collected = new HashMap<>();
        headers.applyTo(collected::put);

        assertEquals("max-age=3600", collected.get(SecurityHeaders.HEADER_HSTS));
        assertEquals("SAMEORIGIN", collected.get(SecurityHeaders.HEADER_X_FRAME_OPTIONS));
        assertEquals("nosniff", collected.get(SecurityHeaders.HEADER_X_CONTENT_TYPE_OPTIONS));
        assertEquals("Secured", collected.get("X-Custom-Header"));
    }

    @Test
    @DisplayName("Policy validator detects unsafe inline without nonce/hash and wildcard scripts")
    void testValidatorUnsafeCsp() {
        SecurityHeaders headers = SecurityHeaders.builder()
                .csp(CspConfig.builder()
                        .scriptSrc("'unsafe-inline'", "*")
                        .build())
                .build();

        SecurityPolicyValidator.ValidationReport report = SecurityPolicyValidator.validate(headers);
        assertFalse(report.isValid());
        assertTrue(report.issues().stream().anyMatch(i -> i.message().contains("Wildcard '*'")));
        assertTrue(report.issues().stream().anyMatch(i -> i.message().contains("'unsafe-inline'")));
    }

    @Test
    @DisplayName("Policy validator detects HSTS preload misconfiguration")
    void testValidatorHstsPreload() {
        SecurityHeaders headers = SecurityHeaders.builder()
                .hsts(1000, false, true) // preload but maxAge < 1 year and no subdomains
                .build();

        SecurityPolicyValidator.ValidationReport report = SecurityPolicyValidator.validate(headers);
        assertFalse(report.isValid());
        assertTrue(report.issues().stream().anyMatch(i -> i.message().contains("max-age to be at least 31,536,000")));
        assertTrue(report.issues().stream().anyMatch(i -> i.message().contains("includeSubDomains")));
    }

    @Test
    @DisplayName("Policy validator warns about unsafe referrer and missing framing protection")
    void testValidatorFramingAndReferrer() {
        SecurityHeaders headers = SecurityHeaders.builder()
                .referrerPolicy(ReferrerPolicy.UNSAFE_URL)
                .contentTypeOptionsNosniff(false)
                .build();

        SecurityPolicyValidator.ValidationReport report = SecurityPolicyValidator.validate(headers);
        assertTrue(report.hasWarnings());
        assertTrue(report.issues().stream().anyMatch(i -> i.headerName().equals(SecurityHeaders.HEADER_REFERRER_POLICY)));
        assertTrue(report.issues().stream().anyMatch(i -> i.headerName().equals(SecurityHeaders.HEADER_X_FRAME_OPTIONS)));
        assertTrue(report.issues().stream().anyMatch(i -> i.headerName().equals(SecurityHeaders.HEADER_X_CONTENT_TYPE_OPTIONS)));
    }
}
