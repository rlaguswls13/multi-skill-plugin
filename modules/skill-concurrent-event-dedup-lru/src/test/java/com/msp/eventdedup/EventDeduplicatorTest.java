package com.msp.eventdedup;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class EventDeduplicatorTest {

    @Test
    @DisplayName("Duplicate event within TTL is rejected on tryAcquire and detected by isDuplicate")
    void testDuplicateRejectionWithinTtl() {
        EventDeduplicator dedup = new EventDeduplicator(100, Duration.ofMinutes(5));

        assertTrue(dedup.tryAcquire("evt-100"));
        assertTrue(dedup.isDuplicate("evt-100"));

        // Second attempt fails
        assertFalse(dedup.tryAcquire("evt-100"));
        assertEquals(1, dedup.size());
    }

    @Test
    @DisplayName("Event expires after TTL and can be acquired again")
    void testExpirationAfterTtl() throws InterruptedException {
        EventDeduplicator dedup = new EventDeduplicator(100, Duration.ofMillis(50));

        assertTrue(dedup.tryAcquire("evt-exp"));
        assertFalse(dedup.tryAcquire("evt-exp"));

        // Wait for TTL expiration
        Thread.sleep(70);

        assertFalse(dedup.isDuplicate("evt-exp"));
        assertEquals(0, dedup.size());

        // Can acquire again after expiration
        assertTrue(dedup.tryAcquire("evt-exp"));
    }

    @Test
    @DisplayName("Release removes in-flight event allowing retry, but markProcessed retains it")
    void testMarkProcessedVsRelease() {
        EventDeduplicator dedup = new EventDeduplicator(100, Duration.ofMinutes(1));

        // Case 1: In-flight event fails downstream -> release allows immediate retry
        assertTrue(dedup.tryAcquire("evt-fail"));
        assertFalse(dedup.tryAcquire("evt-fail"));

        dedup.release("evt-fail"); // simulate processing failure
        assertTrue(dedup.tryAcquire("evt-fail")); // retry succeeds!

        // Case 2: In-flight event succeeds -> markProcessed locks in idempotency
        dedup.markProcessed("evt-fail");
        assertFalse(dedup.tryAcquire("evt-fail"));

        // Calling release on a PROCESSED event should NOT remove it
        dedup.release("evt-fail");
        assertFalse(dedup.tryAcquire("evt-fail"));
        assertTrue(dedup.isDuplicate("evt-fail"));
    }

    @Test
    @DisplayName("Segment LRU evicts eldest entries when segment capacity is exceeded")
    void testCapacityLruEviction() {
        // Use concurrencyLevel=1 to guarantee all items enter the same LRU segment
        int capacity = 5;
        EventDeduplicator dedup = new EventDeduplicator(capacity, Duration.ofMinutes(10), 1);

        for (int i = 0; i < capacity; i++) {
            assertTrue(dedup.tryAcquire("key-" + i));
        }
        assertEquals(5, dedup.size());

        // Adding 6th key evicts the eldest key ("key-0")
        assertTrue(dedup.tryAcquire("key-5"));
        assertEquals(5, dedup.size());

        // "key-0" was evicted, so it is no longer considered duplicate
        assertFalse(dedup.isDuplicate("key-0"));
        // "key-1" to "key-5" are still present
        assertTrue(dedup.isDuplicate("key-1"));
        assertTrue(dedup.isDuplicate("key-5"));
    }

    @Test
    @DisplayName("cleanupExpired purges all stale entries and reports correct removal count")
    void testCleanupExpired() throws InterruptedException {
        EventDeduplicator dedup = new EventDeduplicator(100, Duration.ofMillis(40));

        dedup.tryAcquire("a");
        dedup.tryAcquire("b");
        dedup.tryAcquire("c");
        assertEquals(3, dedup.size());

        Thread.sleep(60);

        int cleaned = dedup.cleanupExpired();
        assertEquals(3, cleaned);
        assertEquals(0, dedup.size());
    }

    @Test
    @DisplayName("Multi-threaded concurrency burst guarantees exactly one winner per unique eventId")
    void testMultiThreadConcurrencyBurst() throws Exception {
        int threads = 16;
        int operationsPerThread = 200;
        int uniqueKeys = 20;

        EventDeduplicator dedup = new EventDeduplicator(5000, Duration.ofMinutes(5), 16);
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(1);

        AtomicInteger successfulAcquires = new AtomicInteger(0);
        List<Future<?>> futures = new ArrayList<>();

        for (int t = 0; t < threads; t++) {
            futures.add(executor.submit(() -> {
                try {
                    latch.await();
                    for (int i = 0; i < operationsPerThread; i++) {
                        String key = "event-" + (i % uniqueKeys);
                        if (dedup.tryAcquire(key)) {
                            successfulAcquires.incrementAndGet();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
        }

        latch.countDown();
        for (Future<?> f : futures) {
            f.get();
        }
        executor.shutdown();

        // Exactly 'uniqueKeys' should have succeeded in tryAcquire across all threads
        assertEquals(uniqueKeys, successfulAcquires.get());
        assertEquals(uniqueKeys, dedup.size());
    }

    @Test
    @DisplayName("clear empties all cached events")
    void testClear() {
        EventDeduplicator dedup = new EventDeduplicator(100, Duration.ofMinutes(5));
        dedup.tryAcquire("e1");
        dedup.tryAcquire("e2");
        assertEquals(2, dedup.size());

        dedup.clear();
        assertEquals(0, dedup.size());
        assertFalse(dedup.isDuplicate("e1"));
    }
}
