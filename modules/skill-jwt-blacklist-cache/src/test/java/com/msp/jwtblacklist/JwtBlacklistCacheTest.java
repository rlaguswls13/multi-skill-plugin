package com.msp.jwtblacklist;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class JwtBlacklistCacheTest {

    @Test
    @DisplayName("Revoked token is identified as revoked until expiration")
    void testRevokeAndCheck() throws InterruptedException {
        JwtBlacklistCache cache = new JwtBlacklistCache();
        String jti = "jwt-unique-id-12345";

        assertFalse(cache.isRevoked(jti));

        // Revoke for 100 milliseconds
        cache.revoke(jti, Duration.ofMillis(100));
        assertTrue(cache.isRevoked(jti));
        assertEquals(1, cache.size());

        // Wait for expiration
        Thread.sleep(120);
        assertFalse(cache.isRevoked(jti)); // Lazy eviction
        assertEquals(0, cache.size());
    }

    @Test
    @DisplayName("Already expired token is not stored")
    void testExpiredNotStored() {
        JwtBlacklistCache cache = new JwtBlacklistCache();
        String token = "old-token";

        cache.revoke(token, Instant.now().minusSeconds(10));
        assertFalse(cache.isRevoked(token));
        assertEquals(0, cache.size());
    }

    @Test
    @DisplayName("Active cleanup purges expired tokens batch")
    void testCleanupExpired() throws InterruptedException {
        JwtBlacklistCache cache = new JwtBlacklistCache();

        cache.revoke("t1", Duration.ofMillis(50));
        cache.revoke("t2", Duration.ofHours(1));
        assertEquals(2, cache.size());

        Thread.sleep(80);

        int cleaned = cache.cleanupExpired();
        assertEquals(1, cleaned);
        assertEquals(1, cache.size());
        assertTrue(cache.isRevoked("t2"));
        assertFalse(cache.isRevoked("t1"));
    }
}
