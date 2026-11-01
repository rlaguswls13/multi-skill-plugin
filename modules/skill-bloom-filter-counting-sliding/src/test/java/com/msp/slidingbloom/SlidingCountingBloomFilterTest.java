package com.msp.slidingbloom;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SlidingCountingBloomFilter Unit Tests")
class SlidingCountingBloomFilterTest {

    private static final class TestClock extends Clock {
        private final AtomicLong millis;
        private final ZoneId zone = ZoneId.of("UTC");

        TestClock(long initialMillis) {
            this.millis = new AtomicLong(initialMillis);
        }

        void advance(Duration duration) {
            millis.addAndGet(duration.toMillis());
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
            return Instant.ofEpochMilli(millis.get());
        }

        @Override
        public long millis() {
            return millis.get();
        }
    }

    @Test
    @DisplayName("Single-window addition and zero false negatives")
    void testBasicAddAndContain() {
        TestClock clock = new TestClock(10_000_000L);
        SlidingCountingBloomFilter<String> filter = new SlidingCountingBloomFilter<>(
                500, 0.01, Duration.ofSeconds(60), 6, clock
        );

        String[] sampleItems = {"alpha", "beta", "gamma", "delta", "user-10023", "order#9887"};

        for (String item : sampleItems) {
            assertFalse(filter.mightContain(item), "Item should not exist before addition");
            filter.add(item);
            assertTrue(filter.mightContain(item), "Item must exist immediately after addition");
        }

        assertEquals(1, filter.activeBucketCount());
    }

    @Test
    @DisplayName("Items expire as time slides across buckets")
    void testTimeSlidingAndExpiration() {
        TestClock clock = new TestClock(100_000L);
        Duration window = Duration.ofSeconds(10);
        int bucketCount = 5; // 2 seconds per bucket
        SlidingCountingBloomFilter<String> filter = new SlidingCountingBloomFilter<>(
                200, 0.01, window, bucketCount, clock
        );

        // Add item at T=0
        filter.add("session-abc");
        assertTrue(filter.mightContain("session-abc"));
        assertEquals(1, filter.activeBucketCount());

        // Advance 4 seconds (2 buckets forward, still inside 10s window)
        clock.advance(Duration.ofSeconds(4));
        filter.add("session-def");
        assertTrue(filter.mightContain("session-abc"), "session-abc should still be active");
        assertTrue(filter.mightContain("session-def"), "session-def should be active");
        assertEquals(2, filter.activeBucketCount());

        // Advance another 7 seconds (total 11 seconds since session-abc, 7 seconds since session-def)
        clock.advance(Duration.ofSeconds(7));

        // session-abc (inserted at 0s) is now outside the 10-second window!
        assertFalse(filter.mightContain("session-abc"), "session-abc must have expired after 11 seconds");
        // session-def (inserted at 4s) is 7 seconds old, still inside 10-second window!
        assertTrue(filter.mightContain("session-def"), "session-def should still be active");

        // Purge expired buckets
        int purged = filter.purgeExpiredBuckets();
        assertTrue(purged >= 1, "Should have purged at least the oldest bucket");

        // Advance past all insertions (another 10 seconds)
        clock.advance(Duration.ofSeconds(10));
        assertFalse(filter.mightContain("session-def"), "session-def should expire as well");
        assertEquals(0, filter.activeBucketCount());
    }

    @Test
    @DisplayName("Clear removes all items immediately")
    void testClear() {
        SlidingCountingBloomFilter<String> filter = new SlidingCountingBloomFilter<>(
                100, 0.01, Duration.ofSeconds(30), 3
        );

        filter.add("item1");
        filter.add("item2");
        assertTrue(filter.mightContain("item1"));
        assertTrue(filter.mightContain("item2"));

        filter.clear();

        assertFalse(filter.mightContain("item1"));
        assertFalse(filter.mightContain("item2"));
        assertEquals(0, filter.activeBucketCount());
    }

    @Test
    @DisplayName("Concurrent reads and writes under multi-threading")
    void testConcurrentAccess() throws InterruptedException {
        int threadCount = 8;
        int itemsPerThread = 200;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        SlidingCountingBloomFilter<String> filter = new SlidingCountingBloomFilter<>(
                2000, 0.01, Duration.ofSeconds(60), 6
        );

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            pool.submit(() -> {
                try {
                    startLatch.await();
                    for (int i = 0; i < itemsPerThread; i++) {
                        String item = "thread-" + threadId + "-key-" + i;
                        filter.add(item);
                        assertTrue(filter.mightContain(item));
                    }
                } catch (Exception e) {
                    fail(e);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS), "Concurrent operations timed out");
        pool.shutdown();

        // Verify items are preserved
        for (int t = 0; t < threadCount; t++) {
            for (int i = 0; i < itemsPerThread; i++) {
                String item = "thread-" + t + "-key-" + i;
                assertTrue(filter.mightContain(item));
            }
        }
    }

    @Test
    @DisplayName("Invalid arguments validation")
    void testInvalidArguments() {
        assertThrows(IllegalArgumentException.class, () ->
                new SlidingCountingBloomFilter<>(0, 0.01, Duration.ofSeconds(10), 5));
        assertThrows(IllegalArgumentException.class, () ->
                new SlidingCountingBloomFilter<>(100, 0.0, Duration.ofSeconds(10), 5));
        assertThrows(IllegalArgumentException.class, () ->
                new SlidingCountingBloomFilter<>(100, 1.0, Duration.ofSeconds(10), 5));
        assertThrows(IllegalArgumentException.class, () ->
                new SlidingCountingBloomFilter<>(100, 0.01, Duration.ofSeconds(-1), 5));
        assertThrows(IllegalArgumentException.class, () ->
                new SlidingCountingBloomFilter<>(100, 0.01, Duration.ofSeconds(10), 0));
    }

    @Test
    @DisplayName("Murmur3 128-bit hash consistency and deterministic output")
    void testMurmur3Hashing() {
        byte[] data1 = "Hello Bloom Filter".getBytes();
        byte[] data2 = "Hello Bloom Filter".getBytes();
        byte[] data3 = "Different Data".getBytes();

        long[] h1 = Murmur3.hash128(data1, 0, data1.length, 0L);
        long[] h2 = Murmur3.hash128(data2, 0, data2.length, 0L);
        long[] h3 = Murmur3.hash128(data3, 0, data3.length, 0L);

        assertEquals(h1[0], h2[0]);
        assertEquals(h1[1], h2[1]);
        assertNotEquals(h1[0], h3[0]);
    }
}
