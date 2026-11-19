package com.msp.dpop;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory cache to detect and prevent DPoP proof replay attacks.
 */
public final class ReplayCache {

    private final ConcurrentHashMap<String, Instant> cache = new ConcurrentHashMap<>();

    /**
     * Attempts to register the JTI.
     * Returns true if the JTI is new (fresh, not replayed).
     * Returns false if the JTI was already seen within the active window.
     */
    public boolean testAndSet(String jti, Instant expiresAt) {
        if (jti == null || jti.isBlank()) {
            return false;
        }

        cleanupExpired();

        Instant existing = cache.putIfAbsent(jti, expiresAt);
        if (existing == null) {
            return true;
        }

        // If existing is expired, replace it
        if (Instant.now().isAfter(existing)) {
            return cache.replace(jti, existing, expiresAt);
        }

        return false; // JTI already exists and is active -> replay detected!
    }

    public void clear() {
        cache.clear();
    }

    public int size() {
        cleanupExpired();
        return cache.size();
    }

    private void cleanupExpired() {
        Instant now = Instant.now();
        cache.entrySet().removeIf(entry -> now.isAfter(entry.getValue()));
    }
}
