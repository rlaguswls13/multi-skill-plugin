package com.msp.cors;

import java.util.*;

/**
 * W3C CORS and Preflight Request Compliance Evaluator.
 * Evaluates incoming requests against a given CorsConfig and determines if cross-origin access is allowed,
 * generating the appropriate Access-Control-* response headers or rejection reasons.
 */
public final class CorsEvaluator {

    public static final String HEADER_ALLOW_ORIGIN = "Access-Control-Allow-Origin";
    public static final String HEADER_ALLOW_CREDENTIALS = "Access-Control-Allow-Credentials";
    public static final String HEADER_ALLOW_METHODS = "Access-Control-Allow-Methods";
    public static final String HEADER_ALLOW_HEADERS = "Access-Control-Allow-Headers";
    public static final String HEADER_MAX_AGE = "Access-Control-Max-Age";
    public static final String HEADER_EXPOSE_HEADERS = "Access-Control-Expose-Headers";
    public static final String HEADER_ALLOW_PRIVATE_NETWORK = "Access-Control-Allow-Private-Network";
    public static final String HEADER_VARY = "Vary";

    private final CorsConfig config;

    public CorsEvaluator(CorsConfig config) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
    }

    public CorsConfig getConfig() {
        return config;
    }

    /**
     * Evaluates the CORS request and produces a CorsEvaluationResult.
     */
    public CorsEvaluationResult evaluate(CorsRequest request) {
        if (request == null || !request.isCorsRequest()) {
            return CorsEvaluationResult.notCors();
        }

        if (request.isPreflightRequest()) {
            return evaluatePreflight(request);
        } else {
            return evaluateActual(request);
        }
    }

    private CorsEvaluationResult evaluatePreflight(CorsRequest request) {
        String origin = request.getOrigin();

        // 1. Origin check
        if (!config.isOriginAllowed(origin)) {
            return CorsEvaluationResult.rejected(true, "Origin '" + origin + "' is not allowed");
        }

        // 2. Method check
        String reqMethod = request.getRequestMethod();
        if (reqMethod == null || !config.isMethodAllowed(reqMethod)) {
            return CorsEvaluationResult.rejected(true, "Requested method '" + reqMethod + "' is not allowed");
        }

        // 3. Headers check
        for (String reqHeader : request.getRequestHeaders()) {
            if (!config.isHeaderAllowed(reqHeader)) {
                return CorsEvaluationResult.rejected(true, "Requested header '" + reqHeader + "' is not allowed");
            }
        }

        // 4. Build headers
        Map<String, String> headers = new LinkedHashMap<>();

        // W3C Fetch spec: Wildcard '*' MUST NOT be used if credentials are allowed
        if (config.isAllowCredentials()) {
            headers.put(HEADER_ALLOW_ORIGIN, origin);
            headers.put(HEADER_ALLOW_CREDENTIALS, "true");
        } else {
            if (config.isAllOriginsAllowed()) {
                headers.put(HEADER_ALLOW_ORIGIN, CorsConfig.WILDCARD);
            } else {
                headers.put(HEADER_ALLOW_ORIGIN, origin);
            }
        }

        // Allowed methods
        if (config.getAllowedMethods().contains(CorsConfig.WILDCARD)) {
            headers.put(HEADER_ALLOW_METHODS, reqMethod);
        } else {
            headers.put(HEADER_ALLOW_METHODS, String.join(", ", config.getAllowedMethods()));
        }

        // Allowed headers
        if (config.isAllHeadersAllowed()) {
            if (request.getRequestHeaders().isEmpty()) {
                headers.put(HEADER_ALLOW_HEADERS, CorsConfig.WILDCARD);
            } else {
                headers.put(HEADER_ALLOW_HEADERS, String.join(", ", request.getRequestHeaders()));
            }
        } else if (!config.getAllowedHeaders().isEmpty()) {
            headers.put(HEADER_ALLOW_HEADERS, String.join(", ", config.getAllowedHeaders()));
        }

        // Max age
        if (config.getMaxAge() != null && !config.getMaxAge().isNegative()) {
            headers.put(HEADER_MAX_AGE, String.valueOf(config.getMaxAge().toSeconds()));
        }

        // Private network access
        if (config.isAllowPrivateNetwork() && request.isPrivateNetworkRequest()) {
            headers.put(HEADER_ALLOW_PRIVATE_NETWORK, "true");
        }

        // Vary header
        headers.put(HEADER_VARY, "Origin, Access-Control-Request-Method, Access-Control-Request-Headers");

        return CorsEvaluationResult.allowed(true, headers);
    }

    private CorsEvaluationResult evaluateActual(CorsRequest request) {
        String origin = request.getOrigin();

        // 1. Origin check
        if (!config.isOriginAllowed(origin)) {
            return CorsEvaluationResult.rejected(false, "Origin '" + origin + "' is not allowed");
        }

        // 2. Method check
        if (!config.isMethodAllowed(request.getMethod())) {
            return CorsEvaluationResult.rejected(false, "Method '" + request.getMethod() + "' is not allowed");
        }

        // 3. Build headers
        Map<String, String> headers = new LinkedHashMap<>();

        if (config.isAllowCredentials()) {
            headers.put(HEADER_ALLOW_ORIGIN, origin);
            headers.put(HEADER_ALLOW_CREDENTIALS, "true");
        } else {
            if (config.isAllOriginsAllowed()) {
                headers.put(HEADER_ALLOW_ORIGIN, CorsConfig.WILDCARD);
            } else {
                headers.put(HEADER_ALLOW_ORIGIN, origin);
            }
        }

        if (!config.getExposedHeaders().isEmpty()) {
            headers.put(HEADER_EXPOSE_HEADERS, String.join(", ", config.getExposedHeaders()));
        }

        // Vary header
        if (!CorsConfig.WILDCARD.equals(headers.get(HEADER_ALLOW_ORIGIN))) {
            headers.put(HEADER_VARY, "Origin");
        }

        return CorsEvaluationResult.allowed(false, headers);
    }
}
