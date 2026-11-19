package com.msp.starter.httpnet;

import com.msp.cidr.SubnetInfo;
import com.msp.cors.CorsConfig;
import com.msp.cors.CorsEvaluationResult;
import com.msp.cors.CorsEvaluator;
import com.msp.etag.ConditionalEvaluationResult;
import com.msp.etag.EntityTag;
import com.msp.ipbogon.IpCategory;
import com.msp.ipbogon.IpClassification;
import com.msp.ipbogon.SsrfGuard;
import com.msp.linkheader.LinkEntry;
import com.msp.linkheader.PaginationLinks;
import com.msp.problemdetails.ProblemDetails;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HttpNetworkKitTest {

    @Test
    @DisplayName("1. RFC 7807 Problem Details serialize & deserialize round-trip")
    void testProblemDetails() {
        String json = HttpNetworkKit.problemDetailsJson(404, "Resource Not Found", "Order #42 does not exist");
        assertTrue(json.contains("\"status\":404"));
        assertTrue(json.contains("\"title\":\"Resource Not Found\""));
        assertTrue(json.contains("\"detail\":\"Order #42 does not exist\""));

        ProblemDetails parsed = HttpNetworkKit.parseProblemDetails(json);
        assertEquals(404, parsed.getStatus());
        assertEquals("Resource Not Found", parsed.getTitle());
        assertEquals("Order #42 does not exist", parsed.getDetail());
    }

    @Test
    @DisplayName("2. W3C CORS preflight and actual request evaluation")
    void testCorsEvaluation() {
        CorsConfig config = CorsConfig.builder()
                .allowOrigin("https://app.example.com")
                .allowMethods("GET", "POST", "DELETE")
                .allowHeaders("Authorization", "Content-Type")
                .allowCredentials(true)
                .build();

        CorsEvaluationResult preflightAllowed = HttpNetworkKit.evaluateCorsPreflight(
                config, "https://app.example.com", "DELETE", "Authorization"
        );
        assertTrue(preflightAllowed.isAllowed());
        assertTrue(preflightAllowed.isPreflight());
        assertEquals("https://app.example.com", preflightAllowed.getHeader(CorsEvaluator.HEADER_ALLOW_ORIGIN));
        assertEquals("true", preflightAllowed.getHeader(CorsEvaluator.HEADER_ALLOW_CREDENTIALS));

        CorsEvaluationResult preflightDenied = HttpNetworkKit.evaluateCorsPreflight(
                config, "https://evil.example.net", "GET"
        );
        assertFalse(preflightDenied.isAllowed());
        assertTrue(preflightDenied.getRejectionReason().isPresent());

        CorsEvaluationResult actualAllowed = HttpNetworkKit.evaluateCorsActual(config, "GET", "https://app.example.com");
        assertTrue(actualAllowed.isAllowed());
        assertFalse(actualAllowed.isPreflight());
    }

    @Test
    @DisplayName("3. RFC 7232 ETag and conditional request evaluation (304 / 412 / 200)")
    void testEtagConditionalEvaluation() {
        EntityTag etag = HttpNetworkKit.parseEtag("\"v1-abc\"");
        assertFalse(etag.isWeak());
        assertEquals("v1-abc", etag.getOpaqueTag());

        ConditionalEvaluationResult notModified = HttpNetworkKit.evaluateEtag(
                "\"v1-abc\"", Map.of("If-None-Match", "\"v1-abc\""), "GET"
        );
        assertFalse(notModified.isPassed());
        assertEquals(304, notModified.getStatusCode());

        ConditionalEvaluationResult preconditionFailed = HttpNetworkKit.evaluateConditional(
                "\"v2-def\"", Instant.parse("2026-01-01T00:00:00Z"),
                Map.of("If-Match", "\"v1-abc\""), "PUT"
        );
        assertFalse(preconditionFailed.isPassed());
        assertEquals(412, preconditionFailed.getStatusCode());

        ConditionalEvaluationResult passed = HttpNetworkKit.evaluateEtag(
                "\"v2-def\"", Map.of("If-None-Match", "\"v1-abc\""), "GET"
        );
        assertTrue(passed.isPassed());
        assertEquals(200, passed.getStatusCode());
    }

    @Test
    @DisplayName("4. RFC 8288 Link Header parse, format, and pagination links")
    void testLinkHeaderAndPagination() {
        String header = HttpNetworkKit.buildPaginationLinkHeader(
                "https://api.example.com/items?page=1",
                "https://api.example.com/items?page=2",
                "https://api.example.com/items?page=4",
                "https://api.example.com/items?page=10"
        );

        List<LinkEntry> entries = HttpNetworkKit.parseLinkHeader(header);
        assertEquals(4, entries.size());
        assertEquals(header, HttpNetworkKit.formatLinkHeader(entries));

        PaginationLinks links = HttpNetworkKit.parsePaginationLinks(header);
        assertTrue(links.hasPrev());
        assertTrue(links.hasNext());
        assertEquals("https://api.example.com/items?page=1", links.firstUri().orElseThrow());
        assertEquals("https://api.example.com/items?page=4", links.nextUri().orElseThrow());
        assertEquals("https://api.example.com/items?page=10", links.lastUri().orElseThrow());
    }

    @Test
    @DisplayName("5. IP Private/Bogon classification and strict SSRF guard")
    void testIpBogonAndSsrfGuard() {
        assertTrue(HttpNetworkKit.isPublicIp("8.8.8.8"));
        assertFalse(HttpNetworkKit.isBogonIp("8.8.8.8"));

        IpClassification imds = HttpNetworkKit.classifyIp("169.254.169.254");
        assertEquals(IpCategory.CLOUD_METADATA, imds.category());
        assertTrue(HttpNetworkKit.isBogonIp("169.254.169.254"));

        SsrfGuard.Decision blockedMapped = HttpNetworkKit.checkSsrfStrict("::ffff:127.0.0.1");
        assertFalse(blockedMapped.allowed());
        assertEquals(IpCategory.LOOPBACK, blockedMapped.classification().category());

        assertThrows(SecurityException.class, () -> HttpNetworkKit.assertSsrfAllowedStrict("10.0.0.5"));
        assertEquals(IpCategory.PUBLIC_UNICAST, HttpNetworkKit.assertSsrfAllowedStrict("1.1.1.1").category());
    }

    @Test
    @DisplayName("6. IPv4 CIDR subnet calculation, containment, and overlap")
    void testCidrCalculator() {
        SubnetInfo info = HttpNetworkKit.calculateSubnet("192.168.10.42/24");
        assertEquals("192.168.10.0/24", info.cidr());
        assertEquals("192.168.10.0", info.networkAddress());
        assertEquals("192.168.10.255", info.broadcastAddress());
        assertEquals("255.255.255.0", info.netmask());
        assertEquals("192.168.10.1", info.firstHost());
        assertEquals("192.168.10.254", info.lastHost());
        assertEquals(254, info.usableHosts());

        assertTrue(HttpNetworkKit.cidrContains("192.168.10.0/24", "192.168.10.99"));
        assertFalse(HttpNetworkKit.cidrContains("192.168.10.0/24", "192.168.11.1"));

        assertTrue(HttpNetworkKit.cidrOverlaps("10.0.0.0/16", "10.0.5.0/24"));
        assertFalse(HttpNetworkKit.cidrOverlaps("10.0.0.0/24", "10.0.1.0/24"));
    }
}
