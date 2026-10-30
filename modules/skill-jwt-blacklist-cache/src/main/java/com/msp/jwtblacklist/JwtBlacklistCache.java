package com.msp.jwtblacklist;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Thread-safe in-memory cache for revoked JWTs (Blacklist / Denylist) based on JTI or Token signatures.
 * Automatically evicts or ignores tokens whose expiration has passed.
 */
public class JwtBlacklistCache {

    private final ConcurrentMap<String, Instant> blacklist = new ConcurrentHashMap<>();

    /**
     * Revokes a JWT or JTI until the specified expiration instant.
     *
     * @param tokenOrJti the JWT string or unique JTI claim
     * @param expiresAt  the token's expiration timestamp
     */
    public void revoke(String tokenOrJti, Instant expiresAt) {
        Objects.requireNonNull(tokenOrJti, "tokenOrJti must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");

        // Do not store if already expired
        if (expiresAt.isAfter(Instant.now())) {
            blacklist.put(tokenOrJti, expiresAt);
        }
    }

    /**
     * Revokes a JWT or JTI with a relative Time-To-Live (TTL).
     */
    public void revoke(String tokenOrJti, Duration ttl) {
        Objects.requireNonNull(ttl, "ttl must not be null");
        revoke(tokenOrJti, Instant.now().plus(ttl));
    }

    /**
     * Checks if the given token or JTI is currently revoked.
     * If the token's expiration instant has already passed, it is lazily removed and considered unrevoked (expired naturally).
     *
     * @param tokenOrJti the token or JTI to check
     * @return true if actively revoked, false otherwise
     */
    public boolean isRevoked(String tokenOrJti) {
        if (tokenOrJti == null) {
            return false;
        }
        Instant expiresAt = blacklist.get(tokenOrJti);
        if (expiresAt == null) {
            return false;
        }

        if (Instant.now().isAfter(expiresAt)) {
            blacklist.remove(tokenOrJti, expiresAt);
            return false;
        }
        return true;
    }

    /**
     * Actively evicts all expired tokens from the blacklist.
     *
     * @return number of expired tokens purged
     */
    public int cleanupExpired() {
        Instant now = Instant.now();
        int removedCount = 0;
        for (var entry : blacklist.entrySet()) {
            if (now.isAfter(entry.getValue())) {
                if (blacklist.remove(entry.getKey(), entry.getValue())) {
                    removedCount++;
                }
            }
        }
        return removedCount;
    }

    /**
     * Returns the current number of tracked revoked tokens.
     */
    public int size() {
        return blacklist.size();
    }

    /**
     * Clears all entries from the blacklist.
     */
    public void clear() {
        blacklist.clear();
    }
}
