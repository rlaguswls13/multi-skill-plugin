package com.msp.jwtextract;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Fast, zero-dependency JWT claims and header extractor without token signature verification.
 * Optimized for edge gateways, audit logging, and client-side payload inspection.
 */
public final class JwtClaimsExtractor {

    private JwtClaimsExtractor() {
    }

    /**
     * Parses the payload section of the JWT token into a {@link JwtPayload}.
     *
     * @param token raw JWT string (e.g. "header.payload.signature")
     * @return decoded JwtPayload
     * @throws IllegalArgumentException if token format is invalid
     */
    public static JwtPayload parse(String token) {
        String payloadJson = extractPartJson(token, 1);
        Map<String, Object> claims = SimpleJsonParser.parseObject(payloadJson);
        return JwtPayload.fromMap(claims);
    }

    /**
     * Parses the header section of the JWT token into a {@link JwtHeader}.
     *
     * @param token raw JWT string
     * @return decoded JwtHeader
     * @throws IllegalArgumentException if token format is invalid
     */
    public static JwtHeader parseHeader(String token) {
        String headerJson = extractPartJson(token, 0);
        Map<String, Object> claims = SimpleJsonParser.parseObject(headerJson);
        String alg = Objects.toString(claims.get("alg"), null);
        String typ = Objects.toString(claims.get("typ"), null);
        return new JwtHeader(alg, typ, claims);
    }

    /**
     * Extracts the subject claim ('sub') from the token payload.
     *
     * @param token raw JWT string
     * @return Optional containing the subject, or empty if not present
     */
    public static Optional<String> getSubject(String token) {
        JwtPayload payload = parse(token);
        return Optional.ofNullable(payload.sub());
    }

    /**
     * Checks if the given token is expired relative to current system time.
     * Returns false if the 'exp' claim is omitted.
     *
     * @param token raw JWT string
     * @return true if token is expired, false otherwise
     */
    public static boolean isExpired(String token) {
        JwtPayload payload = parse(token);
        return payload.isExpired();
    }

    /**
     * Retrieves an arbitrary claim object from the payload.
     *
     * @param token     raw JWT string
     * @param claimName key of the claim
     * @return claim value, or null if not found
     */
    public static Object getClaim(String token, String claimName) {
        JwtPayload payload = parse(token);
        return payload.getClaim(claimName);
    }

    /**
     * Retrieves a list of roles or permissions from the token payload.
     *
     * @param token     raw JWT string
     * @param claimName name of the roles/authorities claim
     * @return list of role strings
     */
    public static List<String> getRoleList(String token, String claimName) {
        JwtPayload payload = parse(token);
        return payload.getRoleList(claimName);
    }

    private static String extractPartJson(String token, int partIndex) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("JWT token must not be null or blank");
        }

        String[] parts = token.trim().split("\\.");
        if (parts.length < 2) {
            throw new IllegalArgumentException("Malformed JWT: expected at least 2 parts separated by '.', got " + parts.length);
        }

        if (partIndex >= parts.length) {
            throw new IllegalArgumentException("Requested part index " + partIndex + " out of bounds for token parts " + parts.length);
        }

        byte[] decoded = decodeBase64Url(parts[partIndex]);
        return new String(decoded, StandardCharsets.UTF_8);
    }

    private static byte[] decodeBase64Url(String base64) {
        String padded = base64;
        int rem = base64.length() % 4;
        if (rem == 2) {
            padded += "==";
        } else if (rem == 3) {
            padded += "=";
        }
        return Base64.getUrlDecoder().decode(padded);
    }
}
