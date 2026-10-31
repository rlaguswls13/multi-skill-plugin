package com.msp.keyrotator;

import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;

/**
 * Immutable record representing a rotated cryptographic key used for JWT signing and verification.
 *
 * @param kid unique key identifier
 * @param secretKey raw secret key bytes
 * @param createdAt timestamp when the key was generated
 * @param expiresAt timestamp after which the key cannot be used for verification
 */
public record RotatedKey(
        String kid,
        byte[] secretKey,
        Instant createdAt,
        Instant expiresAt
) {
    public RotatedKey {
        Objects.requireNonNull(kid, "kid cannot be null");
        Objects.requireNonNull(secretKey, "secretKey cannot be null");
        Objects.requireNonNull(createdAt, "createdAt cannot be null");
        Objects.requireNonNull(expiresAt, "expiresAt cannot be null");
        secretKey = secretKey.clone();
    }

    @Override
    public byte[] secretKey() {
        return secretKey.clone();
    }

    /**
     * Checks if this key is expired at system UTC time.
     */
    public boolean isExpired() {
        return isExpiredAt(Instant.now());
    }

    /**
     * Checks if this key is expired at a given instant.
     */
    public boolean isExpiredAt(Instant instant) {
        return instant.isAfter(expiresAt);
    }

    /**
     * Returns the secret key encoded as a URL-safe Base64 string without padding.
     */
    public String secretKeyAsBase64() {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(secretKey);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RotatedKey that)) return false;
        return Objects.equals(kid, that.kid) &&
                Arrays.equals(secretKey, that.secretKey) &&
                Objects.equals(createdAt, that.createdAt) &&
                Objects.equals(expiresAt, that.expiresAt);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(kid, createdAt, expiresAt);
        result = 31 * result + Arrays.hashCode(secretKey);
        return result;
    }

    @Override
    public String toString() {
        return "RotatedKey[kid=" + kid + ", createdAt=" + createdAt + ", expiresAt=" + expiresAt + ", keyLength=" + secretKey.length + "B]";
    }
}
