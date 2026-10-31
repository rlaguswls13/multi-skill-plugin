package com.msp.dedupwindow;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Event Deduplicator Sliding Window Tests")
class EventDeduplicatorTest {

    static class MutableClock extends Clock {
        private Instant now;
        private final ZoneId zone;

        MutableClock(Instant start) {
            this.now = start;
            this.zone = ZoneId.of("UTC");
        }

        public void advance(Duration duration) {
            this.now = this.now.plus(duration);
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
            return now;
        }
    }

    @Test
    @DisplayName("Initial key arrival returns false (not duplicate); second arrival returns true")
    void testBasicDeduplication() {
        try (var dedup = new EventDeduplicator<String>(Duration.ofMinutes(5), 100)) {
            assertFalse(dedup.isDuplicate("event-1"), "First arrival should return false");
            assertTrue(dedup.isDuplicate("event-1"), "Second arrival should return true");
            assertTrue(dedup.isDuplicate("event-1"), "Subsequent arrival should return true");

            assertFalse(dedup.isDuplicate("event-2"), "Different key first arrival should return false");
            assertTrue(dedup.isDuplicate("event-2"), "Different key duplicate should return true");
            assertEquals(2, dedup.size());
        }
    }

    @Test
    @DisplayName("checkOnly inspects key presence without mutating state")
    void testCheckOnly() {
        try (var dedup = new EventDeduplicator<String>(Duration.ofMinutes(1), 100)) {
            assertFalse(dedup.checkOnly("unseen-key"), "Unseen key should be false in checkOnly");
            assertEquals(0, dedup.size(), "checkOnly should not add key");

            assertFalse(dedup.isDuplicate("key-1"));
            assertTrue(dedup.checkOnly("key-1"), "Registered key should be true in checkOnly");
            assertEquals(1, dedup.size());
        }
    }

    @Test
    @DisplayName("Keys expire after window duration has elapsed")
    void testKeyExpiration() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        try (var dedup = new EventDeduplicator<String>(Duration.ofSeconds(10), 100, clock)) {
            assertFalse(dedup.isDuplicate("msg-A"));
            assertTrue(dedup.isDuplicate("msg-A"));
            assertEquals(1, dedup.size());

            // Advance time past window (11 seconds)
            clock.advance(Duration.ofSeconds(11));

            // Should no longer be duplicate
            assertFalse(dedup.checkOnly("msg-A"));
            assertEquals(0, dedup.size());

            // Re-registering after expiration should return false
            assertFalse(dedup.isDuplicate("msg-A"));
            assertTrue(dedup.isDuplicate("msg-A"));
        }
    }

    @Test
    @DisplayName("Max capacity triggers FIFO eviction when full")
    void testMaxCapacityEviction() {
        try (var dedup = new EventDeduplicator<String>(Duration.ofMinutes(10), 3)) {
            assertFalse(dedup.isDuplicate("k1"));
            assertFalse(dedup.isDuplicate("k2"));
            assertFalse(dedup.isDuplicate("k3"));
            assertEquals(3, dedup.size());

            // Adding 4th key exceeds capacity 3 -> oldest key "k1" evicted
            assertFalse(dedup.isDuplicate("k4"));
            assertEquals(3, dedup.size());

            // k1 was evicted, so checking it returns false
            assertFalse(dedup.checkOnly("k1"));
            // k2, k3, k4 remain
            assertTrue(dedup.checkOnly("k2"));
            assertTrue(dedup.checkOnly("k3"));
            assertTrue(dedup.checkOnly("k4"));
        }
    }

    @Test
    @DisplayName("clear() resets all entries")
    void testClear() {
        try (var dedup = new EventDeduplicator<String>(Duration.ofMinutes(5), 100)) {
            dedup.isDuplicate("a");
            dedup.isDuplicate("b");
            assertEquals(2, dedup.size());

            dedup.clear();
            assertEquals(0, dedup.size());
            assertFalse(dedup.isDuplicate("a"));
        }
    }

    @Test
    @DisplayName("Thread-safety: multiple concurrent threads deduplicating identical key")
    void testConcurrentDeduplication() throws Exception {
        int threadCount = 16;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(threadCount);
        AtomicInteger winnerCount = new AtomicInteger(0);

        try (var dedup = new EventDeduplicator<String>(Duration.ofMinutes(1), 1000)) {
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        startGate.await();
                        boolean dup = dedup.isDuplicate("concurrent-msg-100");
                        if (!dup) {
                            winnerCount.incrementAndGet();
                        }
                    } catch (InterruptedException ignored) {
                    } finally {
                        endGate.countDown();
                    }
                });
            }

            startGate.countDown();
            endGate.await();

            assertEquals(1, winnerCount.get(), "Exactly one thread should record initial arrival (dup=false)");
            assertEquals(1, dedup.size());
        } finally {
            executor.shutdown();
        }
    }

    @Test
    @DisplayName("Validation for null keys and illegal arguments")
    void testValidation() {
        assertThrows(IllegalArgumentException.class, () -> new EventDeduplicator<String>(Duration.ZERO, 10));
        assertThrows(IllegalArgumentException.class, () -> new EventDeduplicator<String>(Duration.ofSeconds(-1), 10));
        assertThrows(IllegalArgumentException.class, () -> new EventDeduplicator<String>(Duration.ofSeconds(10), 0));
        assertThrows(IllegalArgumentException.class, () -> new EventDeduplicator<String>(Duration.ofSeconds(10), -5));

        EventDeduplicator<String> dedup = new EventDeduplicator<>(Duration.ofSeconds(10), 10);
        assertThrows(IllegalArgumentException.class, () -> dedup.isDuplicate(null));
        assertThrows(IllegalArgumentException.class, () -> dedup.checkOnly(null));

        dedup.close();
        assertThrows(IllegalStateException.class, () -> dedup.isDuplicate("test"));
        assertThrows(IllegalStateException.class, () -> dedup.checkOnly("test"));
        assertThrows(IllegalStateException.class, dedup::size);
    }
}
