package com.msp.trace;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Immutable distributed trace context adhering to W3C TraceContext and Zipkin B3 specifications.
 *
 * @param traceId      32-character hexadecimal trace identifier (16 bytes, not all zeros)
 * @param spanId       16-character hexadecimal span identifier (8 bytes, not all zeros)
 * @param parentSpanId 16-character hexadecimal parent span identifier (nullable)
 * @param flags        8-bit trace flags (bit 0 = sampled)
 */
public record TraceContext(
        String traceId,
        String spanId,
        String parentSpanId,
        byte flags
) {

    public static final String B3_TRACE_ID = "X-B3-TraceId";
    public static final String B3_SPAN_ID = "X-B3-SpanId";
    public static final String B3_SAMPLED = "X-B3-Sampled";
    public static final String B3_PARENT_SPAN_ID = "X-B3-ParentSpanId";
    public static final String B3_FLAGS = "X-B3-Flags";

    public static final byte FLAG_SAMPLED = 0x01;
    public static final byte FLAG_NOT_SAMPLED = 0x00;

    public TraceContext {
        Objects.requireNonNull(traceId, "traceId must not be null");
        Objects.requireNonNull(spanId, "spanId must not be null");

        traceId = traceId.toLowerCase(Locale.ROOT).trim();
        spanId = spanId.toLowerCase(Locale.ROOT).trim();

        if (traceId.length() != 32 || !isHex(traceId) || isAllZeros(traceId)) {
            throw new IllegalArgumentException("traceId must be 32 non-zero hex characters, got: " + traceId);
        }
        if (spanId.length() != 16 || !isHex(spanId) || isAllZeros(spanId)) {
            throw new IllegalArgumentException("spanId must be 16 non-zero hex characters, got: " + spanId);
        }

        if (parentSpanId != null) {
            parentSpanId = parentSpanId.toLowerCase(Locale.ROOT).trim();
            if (parentSpanId.isEmpty()) {
                parentSpanId = null;
            } else if (parentSpanId.length() != 16 || !isHex(parentSpanId) || isAllZeros(parentSpanId)) {
                throw new IllegalArgumentException("parentSpanId must be 16 non-zero hex characters, got: " + parentSpanId);
            }
        }
    }

    /**
     * Checks if this trace is sampled.
     */
    public boolean isSampled() {
        return (flags & FLAG_SAMPLED) == FLAG_SAMPLED;
    }

    /**
     * Creates a new child span context within the same trace.
     */
    public TraceContext childSpan() {
        return new TraceContext(this.traceId, TraceIdPropagator.randomSpanId(), this.spanId, this.flags);
    }

    /**
     * Creates a new child span context with explicit sampling flag.
     */
    public TraceContext childSpan(boolean sampled) {
        byte newFlags = sampled ? FLAG_SAMPLED : FLAG_NOT_SAMPLED;
        return new TraceContext(this.traceId, TraceIdPropagator.randomSpanId(), this.spanId, newFlags);
    }

    /**
     * Formats to W3C TraceContext {@code traceparent} header string:
     * {@code 00-{traceId}-{spanId}-{flags}}
     */
    public String toTraceParent() {
        return String.format(Locale.ROOT, "00-%s-%s-%02x", traceId, spanId, flags);
    }

    /**
     * Parses a W3C TraceContext {@code traceparent} header string.
     * Example: {@code 00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01}
     */
    public static TraceContext fromTraceParent(String traceparent) {
        if (traceparent == null) {
            throw new IllegalArgumentException("traceparent header must not be null");
        }
        String trimmed = traceparent.trim();
        // Standard W3C traceparent is 55 characters: "00-32hex-16hex-2hex"
        if (trimmed.length() < 55) {
            throw new IllegalArgumentException("Invalid traceparent length: " + trimmed.length() + ", expected >= 55");
        }
        String[] parts = trimmed.split("-");
        if (parts.length < 4) {
            throw new IllegalArgumentException("Invalid traceparent format, expected at least 4 parts: " + trimmed);
        }
        String version = parts[0];
        if (!"00".equals(version)) {
            // Forward compatibility: version ff is invalid, other versions may have additional parts
            if ("ff".equalsIgnoreCase(version)) {
                throw new IllegalArgumentException("Invalid traceparent version ff: " + trimmed);
            }
        }
        String parsedTraceId = parts[1];
        String parsedSpanId = parts[2];
        String flagsStr = parts[3];

        if (flagsStr.length() != 2 || !isHex(flagsStr)) {
            throw new IllegalArgumentException("Invalid trace flags: " + flagsStr);
        }
        byte parsedFlags = (byte) Integer.parseInt(flagsStr, 16);

        return new TraceContext(parsedTraceId, parsedSpanId, null, parsedFlags);
    }

    /**
     * Formats to Zipkin B3 Single Header string.
     * Format: {@code {traceId}-{spanId}-{sampled}-{parentSpanId}}
     */
    public String toB3SingleHeader() {
        char sampledChar = isSampled() ? '1' : '0';
        if (parentSpanId != null) {
            return traceId + "-" + spanId + "-" + sampledChar + "-" + parentSpanId;
        }
        return traceId + "-" + spanId + "-" + sampledChar;
    }

    /**
     * Parses Zipkin B3 Single Header string.
     * Supported formats:
     * - {@code {traceId}-{spanId}}
     * - {@code {traceId}-{spanId}-{sampled}}
     * - {@code {traceId}-{spanId}-{sampled}-{parentSpanId}}
     */
    public static TraceContext fromB3SingleHeader(String b3) {
        if (b3 == null || b3.trim().isEmpty()) {
            throw new IllegalArgumentException("B3 header string must not be empty");
        }
        String[] parts = b3.trim().split("-");
        if (parts.length < 2) {
            throw new IllegalArgumentException("Invalid B3 single header format: " + b3);
        }

        String tId = parts[0];
        // B3 allows 16-hex or 32-hex traceId. If 16 hex chars, pad to 32 hex chars with leading zeros.
        if (tId.length() == 16) {
            tId = "0000000000000000" + tId;
        }

        String sId = parts[1];
        byte f = FLAG_SAMPLED;
        String pId = null;

        if (parts.length >= 3) {
            String sampledPart = parts[2];
            if ("1".equals(sampledPart) || "true".equalsIgnoreCase(sampledPart) || "d".equalsIgnoreCase(sampledPart)) {
                f = FLAG_SAMPLED;
            } else if ("0".equals(sampledPart) || "false".equalsIgnoreCase(sampledPart)) {
                f = FLAG_NOT_SAMPLED;
            }
        }

        if (parts.length >= 4) {
            pId = parts[3];
        }

        return new TraceContext(tId, sId, pId, f);
    }

    /**
     * Formats into Zipkin B3 multi-headers map.
     */
    public Map<String, String> toB3Headers() {
        Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        headers.put(B3_TRACE_ID, traceId);
        headers.put(B3_SPAN_ID, spanId);
        headers.put(B3_SAMPLED, isSampled() ? "1" : "0");
        if (parentSpanId != null) {
            headers.put(B3_PARENT_SPAN_ID, parentSpanId);
        }
        return headers;
    }

    /**
     * Parses from HTTP headers map (case-insensitive lookup).
     */
    public static TraceContext fromB3Headers(Map<String, String> headers) {
        if (headers == null) {
            throw new IllegalArgumentException("Headers map must not be null");
        }
        Map<String, String> ciMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        ciMap.putAll(headers);

        String tId = ciMap.get(B3_TRACE_ID);
        String sId = ciMap.get(B3_SPAN_ID);

        if (tId == null || sId == null) {
            throw new IllegalArgumentException("Missing required B3 headers: " + B3_TRACE_ID + " or " + B3_SPAN_ID);
        }

        if (tId.length() == 16) {
            tId = "0000000000000000" + tId;
        }

        String sampledVal = ciMap.get(B3_SAMPLED);
        byte f = FLAG_SAMPLED;
        if (sampledVal != null) {
            f = ("1".equals(sampledVal) || "true".equalsIgnoreCase(sampledVal) || "d".equalsIgnoreCase(sampledVal))
                    ? FLAG_SAMPLED : FLAG_NOT_SAMPLED;
        }

        String pId = ciMap.get(B3_PARENT_SPAN_ID);
        return new TraceContext(tId, sId, pId, f);
    }

    /**
     * Creates a new sampled trace context with random identifiers.
     */
    public static TraceContext create() {
        return create(true);
    }

    /**
     * Creates a new trace context with random identifiers and specified sampling.
     */
    public static TraceContext create(boolean sampled) {
        return new TraceContext(
                TraceIdPropagator.randomTraceId(),
                TraceIdPropagator.randomSpanId(),
                null,
                sampled ? FLAG_SAMPLED : FLAG_NOT_SAMPLED
        );
    }

    /**
     * Creates a trace context with specified traceId and spanId.
     */
    public static TraceContext of(String traceId, String spanId) {
        return new TraceContext(traceId, spanId, null, FLAG_SAMPLED);
    }

    /**
     * Creates a trace context with specified values.
     */
    public static TraceContext of(String traceId, String spanId, String parentSpanId, boolean sampled) {
        return new TraceContext(traceId, spanId, parentSpanId, sampled ? FLAG_SAMPLED : FLAG_NOT_SAMPLED);
    }

    private static boolean isHex(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean valid = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
            if (!valid) return false;
        }
        return true;
    }

    private static boolean isAllZeros(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) != '0') return false;
        }
        return true;
    }
}
