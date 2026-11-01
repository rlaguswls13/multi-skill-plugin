package com.msp.tokenx;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable record representing an active or expired opaque reference token mapping.
 *
 * @param opaqueToken the cryptographically secure opaque token string
 * @param claims      the claims associated with this reference token
 * @param issuedAt    the issuance timestamp
 * @param expiresAt   the expiration timestamp
 */
public record OpaqueTokenRecord(
        String opaqueToken,
        Map<String, Object> claims,
        Instant issuedAt,
        Instant expiresAt
) {

    public OpaqueTokenRecord {
        Objects.requireNonNull(opaqueToken, "opaqueToken cannot be null");
        Objects.requireNonNull(issuedAt, "issuedAt cannot be null");
        Objects.requireNonNull(expiresAt, "expiresAt cannot be null");
        if (opaqueToken.isBlank()) {
            throw new IllegalArgumentException("opaqueToken cannot be blank");
        }
        if (expiresAt.isBefore(issuedAt)) {
            throw new IllegalArgumentException("expiresAt cannot be before issuedAt");
        }
        claims = claims == null ? Map.of() : Collections.unmodifiableMap(Map.copyOf(claims));
    }

    /**
     * Checks whether this token is expired at the given instant.
     *
     * @param now current instant
     * @return true if now is on or after expiresAt
     */
    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    /**
     * Checks whether this token is expired based on current system time.
     *
     * @return true if expired
     */
    public boolean isExpired() {
        return isExpired(Instant.now());
    }
}
