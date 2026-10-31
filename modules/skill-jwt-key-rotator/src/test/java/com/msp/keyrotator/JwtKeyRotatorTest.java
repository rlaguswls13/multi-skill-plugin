package com.msp.keyrotator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class JwtKeyRotatorTest {

    static class MutableClock extends Clock {
        private final AtomicReference<Instant> currentInstant;
        private final ZoneId zone = ZoneId.of("UTC");

        public MutableClock(Instant initial) {
            this.currentInstant = new AtomicReference<>(initial);
        }

        public void advance(Duration duration) {
            currentInstant.updateAndGet(i -> i.plus(duration));
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

    @Test
    @DisplayName("Initial key creation upon instantiation")
    void testInitialKeyCreation() {
        JwtKeyRotator rotator = new JwtKeyRotator();
        RotatedKey active = rotator.getActiveKey();

        assertNotNull(active);
        assertNotNull(active.kid());
        assertEquals(32, active.secretKey().length);
        assertFalse(active.isExpired());
        assertEquals(1, rotator.getValidKeyCount());

        Optional<RotatedKey> found = rotator.getKey(active.kid());
        assertTrue(found.isPresent());
        assertEquals(active.kid(), found.get().kid());
    }

    @Test
    @DisplayName("Manual key rotation preserves old key during grace period")
    void testManualRotationAndGracePeriod() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        JwtKeyRotator rotator = JwtKeyRotator.builder()
                .clock(clock)
                .rotationInterval(Duration.ofHours(24))
                .gracePeriod(Duration.ofHours(6))
                .build();

        RotatedKey key1 = rotator.getActiveKey();
        assertEquals(1, rotator.getAllValidKeys().size());

        // Rotate key
        RotatedKey key2 = rotator.rotateKey();
        assertNotEquals(key1.kid(), key2.kid());
        assertEquals(key2.kid(), rotator.getActiveKey().kid());

        // Both key1 (grace period) and key2 (active) are valid
        assertEquals(2, rotator.getValidKeyCount());
        assertTrue(rotator.getKey(key1.kid()).isPresent());
        assertTrue(rotator.getKey(key2.kid()).isPresent());

        // Advance 5 hours (within 6 hour grace period)
        clock.advance(Duration.ofHours(5));
        assertTrue(rotator.getKey(key1.kid()).isPresent());
        assertEquals(2, rotator.getValidKeyCount());

        // Advance 2 more hours (total 7 hours > 6 hour grace period)
        clock.advance(Duration.ofHours(2));
        // key1 is now expired
        assertFalse(rotator.getKey(key1.kid()).isPresent());
        assertEquals(1, rotator.getAllValidKeys().size());

        // Calling cleanExpiredKeys returns 0 since lazy purge already cleaned key1
        assertEquals(0, rotator.cleanExpiredKeys());
    }

    @Test
    @DisplayName("Auto rotation on access when active key exceeds rotation interval")
    void testAutoRotationOnAccess() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        JwtKeyRotator rotator = JwtKeyRotator.builder()
                .clock(clock)
                .rotationInterval(Duration.ofHours(12))
                .gracePeriod(Duration.ofHours(4))
                .autoRotateOnAccess(true)
                .build();

        RotatedKey key1 = rotator.getActiveKey();

        // Advance 10 hours (less than 12h rotation interval)
        clock.advance(Duration.ofHours(10));
        assertSame(key1, rotator.getActiveKey());

        // Advance 3 more hours (total 13h > 12h)
        clock.advance(Duration.ofHours(3));
        RotatedKey key2 = rotator.getActiveKey();
        assertNotEquals(key1.kid(), key2.kid());

        // key1 is still within grace period of 4 hours
        assertTrue(rotator.getKey(key1.kid()).isPresent());
    }

    @Test
    @DisplayName("Defensive copying of secret key array in RotatedKey")
    void testDefensiveCopying() {
        byte[] secret = new byte[]{1, 2, 3, 4, 5};
        Instant now = Instant.now();
        RotatedKey key = new RotatedKey("test-kid", secret, now, now.plusSeconds(3600));

        // Mutate original byte array
        secret[0] = 99;
        assertEquals(1, key.secretKey()[0]);

        // Mutate array returned from getter
        byte[] fetched = key.secretKey();
        fetched[0] = 88;
        assertEquals(1, key.secretKey()[0]);

        assertNotNull(key.secretKeyAsBase64());
        assertTrue(key.toString().contains("test-kid"));
    }

    @Test
    @DisplayName("Concurrent key rotation and lookups across virtual threads")
    void testConcurrency() throws Exception {
        JwtKeyRotator rotator = JwtKeyRotator.builder()
                .rotationInterval(Duration.ofMinutes(1))
                .gracePeriod(Duration.ofMinutes(2))
                .build();

        int threadCount = 20;
        CountDownLatch startLatch = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(threadCount)) {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < threadCount; i++) {
                final int idx = i;
                futures.add(executor.submit(() -> {
                    startLatch.await();
                    for (int j = 0; j < 50; j++) {
                        RotatedKey active = rotator.getActiveKey();
                        assertNotNull(active);
                        if (idx % 5 == 0 && j % 10 == 0) {
                            rotator.rotateKey();
                        }
                        rotator.getKey(active.kid());
                    }
                    return null;
                }));
            }
            startLatch.countDown();
            for (Future<?> f : futures) {
                f.get();
            }
        }

        assertTrue(rotator.getValidKeyCount() >= 1);
    }

    @Test
    @DisplayName("Validation and error conditions")
    void testValidation() {
        assertThrows(IllegalArgumentException.class, () -> JwtKeyRotator.builder().keyByteSize(0));
        assertThrows(NullPointerException.class, () -> JwtKeyRotator.builder().rotationInterval(null));
        assertThrows(NullPointerException.class, () -> JwtKeyRotator.builder().gracePeriod(null));
        assertThrows(NullPointerException.class, () -> JwtKeyRotator.builder().clock(null));

        JwtKeyRotator rotator = new JwtKeyRotator();
        assertTrue(rotator.getKey(null).isEmpty());
        assertTrue(rotator.getKey("non-existent-kid").isEmpty());
    }
}
