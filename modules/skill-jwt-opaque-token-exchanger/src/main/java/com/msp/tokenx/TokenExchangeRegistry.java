package com.msp.tokenx;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory registry implementing the RFC 8693 OAuth 2.0 Token Exchange pattern
 * between high-entropy Opaque Reference Tokens and JWT-style Claims sets.
 */
public class TokenExchangeRegistry {

    private static final int TOKEN_BYTE_LENGTH = 32; // 256-bit entropy

    private final SecureRandom secureRandom;
    private final Clock clock;
    private final ConcurrentHashMap<String, OpaqueTokenRecord> tokenStore = new ConcurrentHashMap<>();

    /**
     * Creates a new registry with default {@link SecureRandom} and system UTC clock.
     */
    public TokenExchangeRegistry() {
        this(new SecureRandom(), Clock.systemUTC());
    }

    /**
     * Creates a new registry with the provided {@link SecureRandom} and system UTC clock.
     *
     * @param secureRandom the cryptographically secure random number generator
     */
    public TokenExchangeRegistry(SecureRandom secureRandom) {
        this(secureRandom, Clock.systemUTC());
    }

    /**
     * Creates a new registry with a custom {@link SecureRandom} and {@link Clock} (useful for deterministic tests).
     *
     * @param secureRandom the secure random number generator
     * @param clock        the clock for timestamping and TTL evaluation
     */
    public TokenExchangeRegistry(SecureRandom secureRandom, Clock clock) {
        this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
    }

    /**
     * Issues a new cryptographically secure 256-bit URL-safe opaque reference token mapped to the given claims.
     *
     * @param claims the claims to associate with the token
     * @param ttl    time-to-live duration for the token
     * @return the issued opaque token string (43 characters, URL-safe base64)
     * @throws NullPointerException     if claims or ttl is null
     * @throws IllegalArgumentException if ttl is negative or zero
     */
    public String issueOpaqueToken(Map<String, Object> claims, Duration ttl) {
        Objects.requireNonNull(claims, "claims cannot be null");
        Objects.requireNonNull(ttl, "ttl cannot be null");
        if (ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("TTL duration must be strictly positive: " + ttl);
        }

        byte[] randomBytes = new byte[TOKEN_BYTE_LENGTH];
        secureRandom.nextBytes(randomBytes);
        String opaqueToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        Instant now = clock.instant();
        Instant expiresAt = now.plus(ttl);

        OpaqueTokenRecord record = new OpaqueTokenRecord(opaqueToken, claims, now, expiresAt);
        tokenStore.put(opaqueToken, record);
        return opaqueToken;
    }

    /**
     * Resolves and returns the unmodifiable claims associated with an active (non-expired) opaque token.
     *
     * @param opaqueToken the opaque token to resolve
     * @return an {@link Optional} containing the unmodifiable claims, or empty if token is missing or expired
     */
    public Optional<Map<String, Object>> resolveClaims(String opaqueToken) {
        return getRecord(opaqueToken).map(OpaqueTokenRecord::claims);
    }

    /**
     * Retrieves the {@link OpaqueTokenRecord} for an active (non-expired) opaque token.
     *
     * @param opaqueToken the opaque token
     * @return an {@link Optional} containing the record, or empty if missing or expired
     */
    public Optional<OpaqueTokenRecord> getRecord(String opaqueToken) {
        if (opaqueToken == null || opaqueToken.isBlank()) {
            return Optional.empty();
        }

        OpaqueTokenRecord record = tokenStore.get(opaqueToken);
        if (record == null) {
            return Optional.empty();
        }

        if (record.isExpired(clock.instant())) {
            // Lazy purge upon access
            tokenStore.remove(opaqueToken, record);
            return Optional.empty();
        }

        return Optional.of(record);
    }

    /**
     * Revokes (removes) an opaque token immediately from the registry.
     *
     * @param opaqueToken the token to revoke
     * @return true if the token existed and was removed, false otherwise
     */
    public boolean revoke(String opaqueToken) {
        if (opaqueToken == null || opaqueToken.isBlank()) {
            return false;
        }
        return tokenStore.remove(opaqueToken) != null;
    }

    /**
     * Purges all expired tokens from the registry.
     *
     * @return the number of tokens purged
     */
    public int purgeExpired() {
        Instant now = clock.instant();
        int purgedCount = 0;

        for (Iterator<Map.Entry<String, OpaqueTokenRecord>> it = tokenStore.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, OpaqueTokenRecord> entry = it.next();
            if (entry.getValue().isExpired(now)) {
                it.remove();
                purgedCount++;
            }
        }

        return purgedCount;
    }

    /**
     * Returns the count of currently active (non-expired) tokens in the registry.
     *
     * @return active token count
     */
    public int activeTokenCount() {
        Instant now = clock.instant();
        int active = 0;
        for (OpaqueTokenRecord record : tokenStore.values()) {
            if (!record.isExpired(now)) {
                active++;
            }
        }
        return active;
    }

    /**
     * Clears all tokens from the registry.
     */
    public void clear() {
        tokenStore.clear();
    }
}
