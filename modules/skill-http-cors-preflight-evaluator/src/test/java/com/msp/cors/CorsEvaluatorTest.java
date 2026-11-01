package com.msp.cors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CorsEvaluatorTest {

    @Test
    @DisplayName("Non-CORS request without Origin header is treated as notCors")
    void testNonCorsRequest() {
        CorsConfig config = CorsConfig.permissive();
        CorsEvaluator evaluator = new CorsEvaluator(config);

        CorsRequest request = new CorsRequest("GET", null, null, null, false);
        CorsEvaluationResult result = evaluator.evaluate(request);

        assertTrue(result.isAllowed());
        assertFalse(result.isPreflight());
        assertTrue(result.getResponseHeaders().isEmpty());
        assertTrue(result.getRejectionReason().isEmpty());
    }

    @Test
    @DisplayName("Preflight OPTIONS request success with standard headers")
    void testPreflightSuccess() {
        CorsConfig config = CorsConfig.builder()
                .allowOrigin("https://app.example.com")
                .allowMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowHeaders("Content-Type", "Authorization", "X-Custom-Header")
                .allowCredentials(true)
                .maxAge(Duration.ofHours(2))
                .build();

        CorsEvaluator evaluator = new CorsEvaluator(config);

        CorsRequest preflight = CorsRequest.preflight(
                "https://app.example.com",
                "POST",
                List.of("content-type", "x-custom-header")
        );

        CorsEvaluationResult result = evaluator.evaluate(preflight);

        assertTrue(result.isAllowed());
        assertTrue(result.isPreflight());
        assertEquals("https://app.example.com", result.getHeader(CorsEvaluator.HEADER_ALLOW_ORIGIN));
        assertEquals("true", result.getHeader(CorsEvaluator.HEADER_ALLOW_CREDENTIALS));
        assertEquals("7200", result.getHeader(CorsEvaluator.HEADER_MAX_AGE));
        assertNotNull(result.getHeader(CorsEvaluator.HEADER_ALLOW_METHODS));
        assertNotNull(result.getHeader(CorsEvaluator.HEADER_ALLOW_HEADERS));
        assertNotNull(result.getHeader(CorsEvaluator.HEADER_VARY));
    }

    @Test
    @DisplayName("Preflight rejects unauthorized origin")
    void testPreflightUnauthorizedOrigin() {
        CorsConfig config = CorsConfig.builder()
                .allowOrigin("https://trusted.com")
                .allowMethods("GET", "POST")
                .build();

        CorsEvaluator evaluator = new CorsEvaluator(config);
        CorsRequest preflight = CorsRequest.preflight("https://malicious.com", "POST", List.of("Content-Type"));

        CorsEvaluationResult result = evaluator.evaluate(preflight);

        assertFalse(result.isAllowed());
        assertTrue(result.isPreflight());
        assertTrue(result.getRejectionReason().isPresent());
        assertTrue(result.getRejectionReason().get().contains("https://malicious.com"));
        assertTrue(result.getResponseHeaders().isEmpty());
    }

    @Test
    @DisplayName("Preflight rejects unauthorized HTTP method")
    void testPreflightUnauthorizedMethod() {
        CorsConfig config = CorsConfig.builder()
                .allowOrigin("https://app.example.com")
                .allowMethods("GET", "POST")
                .build();

        CorsEvaluator evaluator = new CorsEvaluator(config);
        CorsRequest preflight = CorsRequest.preflight("https://app.example.com", "DELETE", List.of());

        CorsEvaluationResult result = evaluator.evaluate(preflight);

        assertFalse(result.isAllowed());
        assertTrue(result.getRejectionReason().isPresent());
        assertTrue(result.getRejectionReason().get().contains("DELETE"));
    }

    @Test
    @DisplayName("Preflight rejects unauthorized header")
    void testPreflightUnauthorizedHeader() {
        CorsConfig config = CorsConfig.builder()
                .allowOrigin("https://app.example.com")
                .allowMethods("GET", "POST")
                .allowHeaders("Content-Type", "Authorization")
                .build();

        CorsEvaluator evaluator = new CorsEvaluator(config);
        CorsRequest preflight = CorsRequest.preflight("https://app.example.com", "POST", List.of("X-Evil-Header"));

        CorsEvaluationResult result = evaluator.evaluate(preflight);

        assertFalse(result.isAllowed());
        assertTrue(result.getRejectionReason().isPresent());
        assertTrue(result.getRejectionReason().get().contains("X-Evil-Header"));
    }

    @Test
    @DisplayName("W3C Compliance: Wildcard origin with credentials true reflects the exact origin, never '*'")
    void testWildcardOriginWithCredentialsReflectsOrigin() {
        CorsConfig config = CorsConfig.builder()
                .allowAllOrigins()
                .allowAllMethods()
                .allowAllHeaders()
                .allowCredentials(true)
                .build();

        CorsEvaluator evaluator = new CorsEvaluator(config);
        CorsRequest request = CorsRequest.actual("GET", "https://sub.partner.io");

        CorsEvaluationResult result = evaluator.evaluate(request);

        assertTrue(result.isAllowed());
        // Must be the request origin, not '*'
        assertEquals("https://sub.partner.io", result.getHeader(CorsEvaluator.HEADER_ALLOW_ORIGIN));
        assertEquals("true", result.getHeader(CorsEvaluator.HEADER_ALLOW_CREDENTIALS));
    }

    @Test
    @DisplayName("Wildcard origin without credentials allows '*'")
    void testWildcardOriginWithoutCredentials() {
        CorsConfig config = CorsConfig.builder()
                .allowAllOrigins()
                .allowCredentials(false)
                .build();

        CorsEvaluator evaluator = new CorsEvaluator(config);
        CorsRequest request = CorsRequest.actual("GET", "https://public-api.org");

        CorsEvaluationResult result = evaluator.evaluate(request);

        assertTrue(result.isAllowed());
        assertEquals("*", result.getHeader(CorsEvaluator.HEADER_ALLOW_ORIGIN));
        assertNull(result.getHeader(CorsEvaluator.HEADER_ALLOW_CREDENTIALS));
    }

    @Test
    @DisplayName("Origin wildcard pattern matching (*.example.com)")
    void testOriginPatternMatching() {
        CorsConfig config = CorsConfig.builder()
                .allowOriginPattern("https://*.example.com")
                .allowMethods("GET")
                .build();

        CorsEvaluator evaluator = new CorsEvaluator(config);

        CorsRequest match1 = CorsRequest.actual("GET", "https://api.example.com");
        assertTrue(evaluator.evaluate(match1).isAllowed());

        CorsRequest match2 = CorsRequest.actual("GET", "https://staging.beta.example.com");
        assertTrue(evaluator.evaluate(match2).isAllowed());

        CorsRequest nonMatch = CorsRequest.actual("GET", "https://notexample.com");
        assertFalse(evaluator.evaluate(nonMatch).isAllowed());
    }

    @Test
    @DisplayName("Actual request evaluates exposed headers and Vary header")
    void testActualRequestExposedHeaders() {
        CorsConfig config = CorsConfig.builder()
                .allowOrigin("https://frontend.dev")
                .allowMethods("GET", "POST")
                .exposeHeaders("X-Total-Count", "X-Request-Id")
                .build();

        CorsEvaluator evaluator = new CorsEvaluator(config);
        CorsRequest actual = CorsRequest.actual("GET", "https://frontend.dev");

        CorsEvaluationResult result = evaluator.evaluate(actual);

        assertTrue(result.isAllowed());
        assertFalse(result.isPreflight());
        assertEquals("https://frontend.dev", result.getHeader(CorsEvaluator.HEADER_ALLOW_ORIGIN));
        assertTrue(result.getHeader(CorsEvaluator.HEADER_EXPOSE_HEADERS).contains("X-Total-Count"));
        assertTrue(result.getHeader(CorsEvaluator.HEADER_EXPOSE_HEADERS).contains("X-Request-Id"));
        assertEquals("Origin", result.getHeader(CorsEvaluator.HEADER_VARY));
    }

    @Test
    @DisplayName("Preflight request evaluates private network access")
    void testPrivateNetworkAccess() {
        CorsConfig config = CorsConfig.builder()
                .allowOrigin("https://portal.internal")
                .allowMethods("GET")
                .allowPrivateNetwork(true)
                .build();

        CorsEvaluator evaluator = new CorsEvaluator(config);
        CorsRequest preflight = new CorsRequest("OPTIONS", "https://portal.internal", "GET", List.of(), true);

        CorsEvaluationResult result = evaluator.evaluate(preflight);

        assertTrue(result.isAllowed());
        assertEquals("true", result.getHeader(CorsEvaluator.HEADER_ALLOW_PRIVATE_NETWORK));
    }

    @Test
    @DisplayName("Request created from header map")
    void testFromHeaders() {
        Map<String, String> headers = Map.of(
                "origin", "https://client.site",
                "Access-Control-Request-Method", "PUT",
                "Access-Control-Request-Headers", "authorization, content-type, x-custom"
        );

        CorsRequest req = CorsRequest.fromHeaders("OPTIONS", headers);
        assertTrue(req.isCorsRequest());
        assertTrue(req.isPreflightRequest());
        assertEquals("https://client.site", req.getOrigin());
        assertEquals("PUT", req.getRequestMethod());
        assertEquals(3, req.getRequestHeaders().size());
        assertTrue(req.getRequestHeaders().contains("authorization"));
    }
}
