package com.msp.jwt;

import java.time.Instant;
import java.util.*;

/**
 * Immutable wrapper over standard and custom JWT claims.
 */
public final class JwtClaims {

    public static final String CLAIM_SUBJECT = "sub";
    public static final String CLAIM_ISSUER = "iss";
    public static final String CLAIM_AUDIENCE = "aud";
    public static final String CLAIM_EXPIRATION = "exp";
    public static final String CLAIM_NOT_BEFORE = "nbf";
    public static final String CLAIM_ISSUED_AT = "iat";
    public static final String CLAIM_JWT_ID = "jti";

    private final Map<String, Object> claims;

    public JwtClaims(Map<String, Object> claims) {
        this.claims = claims != null ? Collections.unmodifiableMap(new LinkedHashMap<>(claims)) : Collections.emptyMap();
    }

    public String subject() {
        return getString(CLAIM_SUBJECT);
    }

    public String issuer() {
        return getString(CLAIM_ISSUER);
    }

    public String audience() {
        Object aud = claims.get(CLAIM_AUDIENCE);
        if (aud instanceof String s) return s;
        if (aud instanceof List<?> list && !list.isEmpty()) return String.valueOf(list.get(0));
        return null;
    }

    public List<String> audienceList() {
        Object aud = claims.get(CLAIM_AUDIENCE);
        if (aud instanceof String s) return List.of(s);
        if (aud instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                if (item != null) result.add(item.toString());
            }
            return Collections.unmodifiableList(result);
        }
        return Collections.emptyList();
    }

    public Instant expiresAt() {
        return toInstant(claims.get(CLAIM_EXPIRATION));
    }

    public Instant notBefore() {
        return toInstant(claims.get(CLAIM_NOT_BEFORE));
    }

    public Instant issuedAt() {
        return toInstant(claims.get(CLAIM_ISSUED_AT));
    }

    public String id() {
        return getString(CLAIM_JWT_ID);
    }

    public Object get(String claimName) {
        return claims.get(claimName);
    }

    public String getString(String claimName) {
        Object val = claims.get(claimName);
        return val != null ? val.toString() : null;
    }

    public Long getLong(String claimName) {
        Object val = claims.get(claimName);
        if (val instanceof Number n) return n.longValue();
        if (val instanceof String s) {
            try {
                return Long.parseLong(s);
            } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    public Integer getInteger(String claimName) {
        Object val = claims.get(claimName);
        if (val instanceof Number n) return n.intValue();
        if (val instanceof String s) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    public Boolean getBoolean(String claimName) {
        Object val = claims.get(claimName);
        if (val instanceof Boolean b) return b;
        if (val instanceof String s) return Boolean.parseBoolean(s);
        return null;
    }

    public Map<String, Object> asMap() {
        return claims;
    }

    private static Instant toInstant(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Number n) {
            return Instant.ofEpochSecond(n.longValue());
        }
        if (obj instanceof String s) {
            try {
                return Instant.ofEpochSecond(Long.parseLong(s));
            } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    @Override
    public String toString() {
        return claims.toString();
    }
}
