package com.msp.starter.httpnet;

import com.msp.cidr.CidrCalculator;
import com.msp.cidr.SubnetInfo;
import com.msp.cors.CorsConfig;
import com.msp.cors.CorsEvaluationResult;
import com.msp.cors.CorsEvaluator;
import com.msp.cors.CorsRequest;
import com.msp.etag.ConditionalEvaluationResult;
import com.msp.etag.EntityTag;
import com.msp.etag.HttpConditionalEvaluator;
import com.msp.ipbogon.IpBogonClassifier;
import com.msp.ipbogon.IpClassification;
import com.msp.ipbogon.SsrfGuard;
import com.msp.linkheader.LinkEntry;
import com.msp.linkheader.LinkHeaderParser;
import com.msp.linkheader.PaginationLinks;
import com.msp.problemdetails.ProblemDetails;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * HTTP 프로토콜(RFC 7807 Problem Details, W3C CORS, RFC 7232 ETag, RFC 8288 Link Header) 및
 * 네트워크/IP(Bogon·SSRF 방어, IPv4 CIDR 계산) 기능을 통합 제공하는 스타터 파사드.
 */
public final class HttpNetworkKit {

    private static final SsrfGuard STRICT_SSRF_GUARD = SsrfGuard.strict();

    private HttpNetworkKit() {
    }

    // 1. RFC 7807 / 9457 Problem Details
    public static ProblemDetails problemDetails(int status, String title, String detail) {
        return ProblemDetails.builder()
                .status(status)
                .title(title)
                .detail(detail)
                .build();
    }

    public static String problemDetailsJson(int status, String title, String detail) {
        return problemDetails(status, title, detail).toJson();
    }

    public static String problemDetailsJson(ProblemDetails problem) {
        Objects.requireNonNull(problem, "problem must not be null");
        return problem.toJson();
    }

    public static ProblemDetails parseProblemDetails(String json) {
        return ProblemDetails.fromJson(json);
    }

    // 2. W3C CORS & Preflight Evaluation
    public static CorsEvaluationResult evaluateCors(CorsConfig config, CorsRequest request) {
        return new CorsEvaluator(config).evaluate(request);
    }

    public static CorsEvaluationResult evaluateCorsPreflight(
            CorsConfig config,
            String origin,
            String requestMethod,
            String... requestHeaders
    ) {
        return new CorsEvaluator(config).evaluate(CorsRequest.preflight(origin, requestMethod, requestHeaders));
    }

    public static CorsEvaluationResult evaluateCorsActual(CorsConfig config, String method, String origin) {
        return new CorsEvaluator(config).evaluate(CorsRequest.actual(method, origin));
    }

    // 3. RFC 7232 ETag & Conditional Request Evaluation
    public static EntityTag parseEtag(String rawEtag) {
        return EntityTag.parse(rawEtag);
    }

    public static ConditionalEvaluationResult evaluateConditional(
            String currentEtag,
            Instant lastModified,
            Map<String, String> headers,
            String httpMethod
    ) {
        HttpConditionalEvaluator evaluator = HttpConditionalEvaluator.create();
        if (currentEtag != null && !currentEtag.isBlank()) {
            evaluator = evaluator.withEtag(currentEtag);
        }
        if (lastModified != null) {
            evaluator = evaluator.withLastModified(lastModified);
        }
        return evaluator.evaluate(headers, httpMethod);
    }

    public static ConditionalEvaluationResult evaluateEtag(
            String currentEtag,
            Map<String, String> headers,
            String httpMethod
    ) {
        return evaluateConditional(currentEtag, null, headers, httpMethod);
    }

    // 4. RFC 8288 Link Header & Pagination
    public static List<LinkEntry> parseLinkHeader(String headerValue) {
        return LinkHeaderParser.parse(headerValue);
    }

    public static String formatLinkHeader(List<LinkEntry> entries) {
        return LinkHeaderParser.format(entries);
    }

    public static PaginationLinks parsePaginationLinks(String headerValue) {
        return PaginationLinks.parse(headerValue);
    }

    public static String buildPaginationLinkHeader(
            String firstUri,
            String prevUri,
            String nextUri,
            String lastUri
    ) {
        return PaginationLinks.ofUris(firstUri, prevUri, nextUri, lastUri).toHeaderValue();
    }

    // 5. IP Private / Bogon Classification & SSRF Guard
    public static IpClassification classifyIp(String ipLiteral) {
        return IpBogonClassifier.classify(ipLiteral);
    }

    public static boolean isPublicIp(String ipLiteral) {
        return IpBogonClassifier.isPublic(ipLiteral);
    }

    public static boolean isBogonIp(String ipLiteral) {
        return IpBogonClassifier.isBogon(ipLiteral);
    }

    public static SsrfGuard.Decision checkSsrfStrict(String ipLiteral) {
        return STRICT_SSRF_GUARD.check(ipLiteral);
    }

    public static IpClassification assertSsrfAllowedStrict(String ipLiteral) {
        return STRICT_SSRF_GUARD.assertAllowed(ipLiteral);
    }

    // 6. IPv4 CIDR & Subnet Calculator
    public static SubnetInfo calculateSubnet(String cidr) {
        return CidrCalculator.calculate(cidr);
    }

    public static boolean cidrContains(String cidr, String ipAddress) {
        return CidrCalculator.contains(cidr, ipAddress);
    }

    public static boolean cidrOverlaps(String cidr1, String cidr2) {
        return CidrCalculator.overlaps(cidr1, cidr2);
    }
}
