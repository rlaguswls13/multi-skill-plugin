package com.msp.jwsdetached;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents a parsed or constructed JWS Protected Header and Detached Token ({@code <header>..<signature>}).
 */
public record DetachedJwsToken(
        JwsAlgorithm algorithm,
        boolean b64EncodedPayload,
        String keyId,
        Map<String, String> extraHeaders,
        String encodedHeader,
        String encodedSignature
) {
    private static final Pattern ALG_PATTERN = Pattern.compile("\"alg\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern KID_PATTERN = Pattern.compile("\"kid\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern B64_FALSE_PATTERN = Pattern.compile("\"b64\"\\s*:\\s*false\\b");
    private static final Pattern B64_TRUE_PATTERN = Pattern.compile("\"b64\"\\s*:\\s*true\\b");
    private static final Pattern CRIT_B64_PATTERN = Pattern.compile("\"crit\"\\s*:\\s*\\[[^\\]]*\"b64\"[^\\]]*\\]");

    public DetachedJwsToken {
        Objects.requireNonNull(algorithm, "algorithm must not be null");
        Objects.requireNonNull(encodedHeader, "encodedHeader must not be null");
        Objects.requireNonNull(encodedSignature, "encodedSignature must not be null");
        extraHeaders = extraHeaders == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(extraHeaders));
    }

    /**
     * Returns the compact detached JWS serialization {@code <base64url-header>..<base64url-signature>}.
     */
    public String toCompactDetached() {
        return encodedHeader + ".." + encodedSignature;
    }

    /**
     * Decodes the raw signature bytes from Base64URL.
     */
    public byte[] signatureBytes() {
        return Base64.getUrlDecoder().decode(encodedSignature);
    }

    /**
     * Decodes the UTF-8 JSON header string from Base64URL.
     */
    public String decodedHeaderJson() {
        return new String(Base64.getUrlDecoder().decode(encodedHeader), StandardCharsets.UTF_8);
    }

    /**
     * Parses a compact detached JWS string ({@code <header>..<signature>}).
     */
    public static DetachedJwsToken parse(String compactDetachedJws) {
        Objects.requireNonNull(compactDetachedJws, "compactDetachedJws must not be null");
        int firstDot = compactDetachedJws.indexOf('.');
        int secondDot = compactDetachedJws.indexOf('.', firstDot + 1);
        if (firstDot <= 0 || secondDot != firstDot + 1 || compactDetachedJws.indexOf('.', secondDot + 1) != -1) {
            throw new JwsDetachedException(
                    "Invalid detached JWS format; expected '<header>..<signature>' with empty middle payload");
        }
        String encHeader = compactDetachedJws.substring(0, firstDot);
        String encSig = compactDetachedJws.substring(secondDot + 1);
        if (encSig.isEmpty()) {
            throw new JwsDetachedException("Detached JWS signature must not be empty");
        }

        String json;
        try {
            json = new String(Base64.getUrlDecoder().decode(encHeader), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new JwsDetachedException("Malformed base64url JWS protected header", e);
        }

        Matcher algMatcher = ALG_PATTERN.matcher(json);
        if (!algMatcher.find()) {
            throw new JwsDetachedException("Missing 'alg' in JWS protected header: " + json);
        }
        JwsAlgorithm alg = JwsAlgorithm.fromJoseName(algMatcher.group(1));

        Matcher kidMatcher = KID_PATTERN.matcher(json);
        String kid = kidMatcher.find() ? kidMatcher.group(1) : null;

        boolean b64 = true;
        if (B64_FALSE_PATTERN.matcher(json).find()) {
            b64 = false;
            // RFC 7797 Section 6: when "b64":false is used, "crit" MUST include "b64"
            if (!CRIT_B64_PATTERN.matcher(json).find()) {
                throw new JwsDetachedException("RFC 7797 violation: 'b64':false requires 'crit':['b64'] in header");
            }
        } else if (B64_TRUE_PATTERN.matcher(json).find()) {
            b64 = true;
        }

        return new DetachedJwsToken(alg, b64, kid, Map.of(), encHeader, encSig);
    }

    /**
     * Builds the canonical JSON protected header and returns its Base64URL encoding without padding.
     */
    public static String encodeProtectedHeader(
            JwsAlgorithm algorithm,
            boolean b64EncodedPayload,
            String keyId,
            Map<String, String> extraHeaders) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"alg\":\"").append(escapeJson(algorithm.joseName())).append("\"");
        if (keyId != null && !keyId.isEmpty()) {
            sb.append(",\"kid\":\"").append(escapeJson(keyId)).append("\"");
        }
        if (!b64EncodedPayload) {
            sb.append(",\"b64\":false,\"crit\":[\"b64\"]");
        }
        if (extraHeaders != null) {
            for (Map.Entry<String, String> entry : extraHeaders.entrySet()) {
                String k = entry.getKey();
                if ("alg".equals(k) || "b64".equals(k) || "crit".equals(k) || "kid".equals(k)) {
                    continue;
                }
                sb.append(",\"").append(escapeJson(k)).append("\":\"")
                        .append(escapeJson(entry.getValue())).append("\"");
            }
        }
        sb.append("}");
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
