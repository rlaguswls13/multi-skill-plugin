package com.msp.cors;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Result of evaluating a CORS request.
 */
public final class CorsEvaluationResult {

    private final boolean allowed;
    private final boolean preflight;
    private final Map<String, String> responseHeaders;
    private final String rejectionReason;

    private CorsEvaluationResult(boolean allowed, boolean preflight, Map<String, String> responseHeaders, String rejectionReason) {
        this.allowed = allowed;
        this.preflight = preflight;
        this.responseHeaders = Collections.unmodifiableMap(new LinkedHashMap<>(responseHeaders));
        this.rejectionReason = rejectionReason;
    }

    public static CorsEvaluationResult allowed(boolean preflight, Map<String, String> headers) {
        return new CorsEvaluationResult(true, preflight, headers, null);
    }

    public static CorsEvaluationResult rejected(boolean preflight, String reason) {
        return new CorsEvaluationResult(false, preflight, Collections.emptyMap(), reason);
    }

    public static CorsEvaluationResult notCors() {
        return new CorsEvaluationResult(true, false, Collections.emptyMap(), null);
    }

    public boolean isAllowed() {
        return allowed;
    }

    public boolean isPreflight() {
        return preflight;
    }

    public Map<String, String> getResponseHeaders() {
        return responseHeaders;
    }

    public Optional<String> getRejectionReason() {
        return Optional.ofNullable(rejectionReason);
    }

    public String getHeader(String headerName) {
        if (headerName == null) return null;
        for (Map.Entry<String, String> entry : responseHeaders.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(headerName)) {
                return entry.getValue();
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return "CorsEvaluationResult{" +
                "allowed=" + allowed +
                ", preflight=" + preflight +
                ", responseHeaders=" + responseHeaders +
                ", rejectionReason='" + rejectionReason + '\'' +
                '}';
    }
}
