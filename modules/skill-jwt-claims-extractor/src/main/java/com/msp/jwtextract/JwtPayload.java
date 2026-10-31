package com.msp.jwtextract;

import java.time.Instant;
import java.util.*;

/**
 * Immutable representation of a decoded JWT Payload.
 *
 * @param sub    subject identifier
 * @param iss    issuer
 * @param aud    audience
 * @param exp    expiration time as Instant
 * @param iat    issued at time as Instant
 * @param nbf    not before time as Instant
 * @param jti    JWT unique ID
 * @param claims all raw claims from the payload
 */
public record JwtPayload(
        String sub,
        String iss,
        String aud,
        Instant exp,
        Instant iat,
        Instant nbf,
        String jti,
        Map<String, Object> claims
) {
    public JwtPayload {
        claims = claims != null ? Collections.unmodifiableMap(claims) : Collections.emptyMap();
    }

    public static JwtPayload fromMap(Map<String, Object> claims) {
        String sub = extractString(claims, "sub");
        String iss = extractString(claims, "iss");
        String aud = extractString(claims, "aud");
        String jti = extractString(claims, "jti");

        Instant exp = extractInstant(claims, "exp");
        Instant iat = extractInstant(claims, "iat");
        Instant nbf = extractInstant(claims, "nbf");

        return new JwtPayload(sub, iss, aud, exp, iat, nbf, jti, claims);
    }

    private static String extractString(Map<String, Object> claims, String key) {
        Object val = claims.get(key);
        return val != null ? val.toString() : null;
    }

    private static Instant extractInstant(Map<String, Object> claims, String key) {
        Object val = claims.get(key);
        if (val instanceof Number num) {
            return Instant.ofEpochSecond(num.longValue());
        }
        return null;
    }

    /**
     * Checks if the token is expired relative to current system time.
     * Returns false if 'exp' claim is not present.
     */
    public boolean isExpired() {
        return isExpired(Instant.now());
    }

    /**
     * Checks if the token is expired relative to the given reference instant.
     */
    public boolean isExpired(Instant now) {
        return exp != null && now.isAfter(exp);
    }

    public Object getClaim(String name) {
        return claims.get(name);
    }

    public String getString(String name) {
        Object val = claims.get(name);
        return val != null ? val.toString() : null;
    }

    public Long getLong(String name) {
        Object val = claims.get(name);
        if (val instanceof Number num) {
            return num.longValue();
        }
        return null;
    }

    public Boolean getBoolean(String name) {
        Object val = claims.get(name);
        if (val instanceof Boolean b) {
            return b;
        }
        return null;
    }

    /**
     * Extracts a list of roles or permissions from the given claim name.
     * Supports JSON arrays (["ADMIN", "USER"]) or delimiter-separated strings ("ADMIN,USER" or "read write").
     *
     * @param claimName name of claim (e.g. "roles", "scope", "authorities")
     * @return unmodifiable list of role strings
     */
    public List<String> getRoleList(String claimName) {
        Object val = claims.get(claimName);
        if (val == null) {
            return List.of();
        }
        if (val instanceof List<?> list) {
            return list.stream()
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .toList();
        }
        String str = val.toString().trim();
        if (str.isEmpty()) {
            return List.of();
        }
        String[] tokens = str.split("[,\\s]+");
        return Arrays.stream(tokens)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
