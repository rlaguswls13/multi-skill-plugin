package com.msp.tokenx;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TokenExchangeRegistry Unit Tests")
class TokenExchangeRegistryTest {

    private static final class MutableClock extends Clock {
        private final AtomicReference<Instant> currentInstant;
        private final ZoneId zone;

        MutableClock(Instant initialInstant) {
            this.currentInstant = new AtomicReference<>(initialInstant);
            this.zone = ZoneId.of("UTC");
        }

        void advance(Duration duration) {
            currentInstant.updateAndGet(prev -> prev.plus(duration));
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return currentInstant.get();
        }
    }

    private MutableClock clock;
    private TokenExchangeRegistry registry;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-11-01T12:00:00Z"));
        registry = new TokenExchangeRegistry(new SecureRandom(), clock);
    }

    @Test
    @DisplayName("Issue opaque token and resolve claims successfully")
    void testIssueAndResolve() {
        Map<String, Object> claims = Map.of(
                "sub", "user_12345",
                "scope", "read write",
                "role", "admin"
        );

        String token = registry.issueOpaqueToken(claims, Duration.ofMinutes(15));
        assertNotNull(token);
        assertEquals(43, token.length()); // 32 bytes base64url without padding = 43 chars

        Optional<Map<String, Object>> resolved = registry.resolveClaims(token);
        assertTrue(resolved.isPresent());
        assertEquals("user_12345", resolved.get().get("sub"));
        assertEquals("read write", resolved.get().get("scope"));
        assertEquals("admin", resolved.get().get("role"));

        // Returned claims map is immutable
        assertThrows(UnsupportedOperationException.class, () ->
                resolved.get().put("new_key", "value"));
    }

    @Test
    @DisplayName("Token expires after TTL and cannot be resolved")
    void testTokenExpiration() {
        Map<String, Object> claims = Map.of("sub", "user_abc");
        String token = registry.issueOpaqueToken(claims, Duration.ofSeconds(30));

        // Before expiration
        clock.advance(Duration.ofSeconds(20));
        assertTrue(registry.resolveClaims(token).isPresent());
        assertEquals(1, registry.activeTokenCount());

        // After expiration
        clock.advance(Duration.ofSeconds(15)); // total 35s advanced
        assertFalse(registry.resolveClaims(token).isPresent());
        assertFalse(registry.getRecord(token).isPresent());
        assertEquals(0, registry.activeTokenCount());
    }

    @Test
    @DisplayName("Revoke removes token immediately")
    void testRevokeToken() {
        String token = registry.issueOpaqueToken(Map.of("sub", "user_xyz"), Duration.ofMinutes(5));
        assertEquals(1, registry.activeTokenCount());

        assertTrue(registry.revoke(token));
        assertEquals(0, registry.activeTokenCount());
        assertFalse(registry.resolveClaims(token).isPresent());

        // Revoking non-existent or already revoked returns false
        assertFalse(registry.revoke(token));
        assertFalse(registry.revoke("unknown-token"));
        assertFalse(registry.revoke(null));
        assertFalse(registry.revoke("   "));
    }

    @Test
    @DisplayName("purgeExpired cleans up expired tokens and returns exact count")
    void testPurgeExpired() {
        registry.issueOpaqueToken(Map.of("id", 1), Duration.ofSeconds(10));
        registry.issueOpaqueToken(Map.of("id", 2), Duration.ofSeconds(10));
        registry.issueOpaqueToken(Map.of("id", 3), Duration.ofMinutes(30));

        assertEquals(3, registry.activeTokenCount());

        clock.advance(Duration.ofSeconds(15));
        assertEquals(1, registry.activeTokenCount());

        int purged = registry.purgeExpired();
        assertEquals(2, purged);
        assertEquals(1, registry.activeTokenCount());
    }

    @Test
    @DisplayName("Clear removes all tokens regardless of expiration")
    void testClear() {
        registry.issueOpaqueToken(Map.of("id", 1), Duration.ofMinutes(10));
        registry.issueOpaqueToken(Map.of("id", 2), Duration.ofMinutes(10));
        assertEquals(2, registry.activeTokenCount());

        registry.clear();
        assertEquals(0, registry.activeTokenCount());
    }

    @Test
    @DisplayName("Issued tokens possess 256-bit entropy and high uniqueness")
    void testTokenUniqueness() {
        Set<String> generatedTokens = new HashSet<>();
        int count = 1000;
        for (int i = 0; i < count; i++) {
            String token = registry.issueOpaqueToken(Map.of("seq", i), Duration.ofHours(1));
            assertEquals(43, token.length());
            generatedTokens.add(token);
        }
        assertEquals(count, generatedTokens.size(), "All 1000 issued tokens must be strictly unique");
    }

    @Test
    @DisplayName("Validation and error handling for invalid arguments")
    void testInvalidArguments() {
        assertThrows(NullPointerException.class, () ->
                registry.issueOpaqueToken(null, Duration.ofMinutes(5)));
        assertThrows(NullPointerException.class, () ->
                registry.issueOpaqueToken(Map.of(), null));
        assertThrows(IllegalArgumentException.class, () ->
                registry.issueOpaqueToken(Map.of(), Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () ->
                registry.issueOpaqueToken(Map.of(), Duration.ofSeconds(-5)));

        assertTrue(registry.resolveClaims(null).isEmpty());
        assertTrue(registry.resolveClaims("").isEmpty());
        assertTrue(registry.getRecord(null).isEmpty());
        assertTrue(registry.getRecord("   ").isEmpty());
    }

    @Test
    @DisplayName("Thread-safety under concurrent issuance, resolution, and revocation")
    void testConcurrency() throws InterruptedException {
        int threadCount = 10;
        int tokensPerThread = 200;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        Set<String> allTokens = ConcurrentHashMap.newKeySet();

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < tokensPerThread; i++) {
                        String token = registry.issueOpaqueToken(Map.of("thread", threadId, "seq", i), Duration.ofMinutes(10));
                        allTokens.add(token);

                        Optional<Map<String, Object>> resolved = registry.resolveClaims(token);
                        assertTrue(resolved.isPresent());

                        if (i % 2 == 0) {
                            registry.revoke(token);
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        executor.shutdown();

        assertEquals(threadCount * tokensPerThread, allTokens.size());
        assertEquals((threadCount * tokensPerThread) / 2, registry.activeTokenCount());
    }
}
