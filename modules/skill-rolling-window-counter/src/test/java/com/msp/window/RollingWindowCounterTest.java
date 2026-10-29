package com.msp.window;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RollingWindowCounterTest {

    @Test
    @DisplayName("Exact sums during window with controlled time progression")
    void testExactSums() {
        AtomicLong virtualTime = new AtomicLong(1000L);
        // 10-second window with 10 buckets (1s per bucket)
        RollingWindowCounter counter = RollingWindowCounter.of(Duration.ofSeconds(10), 10, virtualTime::get);

        counter.record(5);
        counter.record(); // +1 = 6
        assertEquals(6, counter.sum());

        // Advance 2 seconds (2 buckets forward)
        virtualTime.addAndGet(2000L);
        counter.record(10);
        assertEquals(16, counter.sum());

        // Advance 5 seconds (7s elapsed, still inside 10s window)
        virtualTime.addAndGet(5000L);
        counter.record(4);
        assertEquals(20, counter.sum());
    }

    @Test
    @DisplayName("Bucket rolling eviction when time advances past window")
    void testBucketEviction() {
        AtomicLong virtualTime = new AtomicLong(0L);
        // 5-second window with 5 buckets (1s each)
        RollingWindowCounter counter = RollingWindowCounter.of(Duration.ofSeconds(5), 5, virtualTime::get);

        // At t=0s, record 10
        counter.record(10);
        assertEquals(10, counter.sum());

        // At t=2s, record 20
        virtualTime.set(2000L);
        counter.record(20);
        assertEquals(30, counter.sum());

        // At t=4s, record 30
        virtualTime.set(4000L);
        counter.record(30);
        assertEquals(60, counter.sum());

        // At t=5s (first bucket at t=0s is evicted! Only t=2s and t=4s buckets remain: 20 + 30 = 50)
        virtualTime.set(5000L);
        assertEquals(50, counter.sum());

        // At t=7s (t=2s bucket evicted! Only t=4s bucket remains: 30)
        virtualTime.set(7000L);
        assertEquals(30, counter.sum());

        // At t=10s (all buckets evicted)
        virtualTime.set(10000L);
        assertEquals(0, counter.sum());
    }

    @Test
    @DisplayName("Rate per second and rate per minute calculation")
    void testRateCalculations() {
        AtomicLong virtualTime = new AtomicLong(0L);
        // 10-second window, 10 buckets
        RollingWindowCounter counter = RollingWindowCounter.of(Duration.ofSeconds(10), 10, virtualTime::get);

        // Record 100 events
        counter.record(100);

        // 100 events / 10s = 10 events/sec
        assertEquals(10.0, counter.ratePerSecond(), 0.001);
        // 10 * 60 = 600 events/min
        assertEquals(600.0, counter.ratePerMinute(), 0.001);

        // Advance 1s, record 50 more (total 150)
        virtualTime.set(1000L);
        counter.record(50);
        assertEquals(15.0, counter.ratePerSecond(), 0.001);
        assertEquals(900.0, counter.ratePerMinute(), 0.001);
    }

    @Test
    @DisplayName("Spike detection when current bucket traffic exceeds threshold multiplier")
    void testSpikeDetection() {
        AtomicLong virtualTime = new AtomicLong(0L);
        // 10-second window with 10 buckets (1s each)
        RollingWindowCounter counter = RollingWindowCounter.of(Duration.ofSeconds(10), 10, virtualTime::get);

        // Simulate steady baseline traffic of 10 events per bucket for 9 buckets (t=0 to t=8s)
        for (int i = 0; i < 9; i++) {
            virtualTime.set(i * 1000L);
            counter.record(10);
        }

        // At t=9s (10th bucket), normal traffic of 12 (1.2x average 10)
        virtualTime.set(9000L);
        counter.record(12);

        // Multiplier 2.0x -> 12 is NOT a spike compared to 10
        assertFalse(counter.isSpike(2.0));
        assertFalse(counter.isSpike(1.5));

        // Sudden surge of 40 events in current bucket (total 52 in current bucket)
        counter.record(40);
        // 52 > 3.0 * 10 = 30 -> Spike detected!
        assertTrue(counter.isSpike(3.0));
        assertTrue(counter.isSpike(4.0));
        // 52 is not > 6.0 * 10 = 60
        assertFalse(counter.isSpike(6.0));
    }

    @Test
    @DisplayName("Concurrent recording under high multi-threaded contention")
    void testConcurrentRecording() throws InterruptedException {
        int threads = 10;
        int incrementsPerThread = 5_000;
        RollingWindowCounter counter = RollingWindowCounter.perMinute(60);

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < incrementsPerThread; j++) {
                        counter.record();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        assertEquals((long) threads * incrementsPerThread, counter.sum());
    }

    @Test
    @DisplayName("Reset clears all buckets")
    void testReset() {
        RollingWindowCounter counter = RollingWindowCounter.perSecond(5);
        counter.record(100);
        assertEquals(100, counter.sum());

        counter.reset();
        assertEquals(0, counter.sum());
    }

    @Test
    @DisplayName("Invalid argument validations")
    void testValidation() {
        assertThrows(IllegalArgumentException.class, () -> new RollingWindowCounter(Duration.ofMillis(0), 10));
        assertThrows(IllegalArgumentException.class, () -> new RollingWindowCounter(Duration.ofSeconds(1), 0));
        RollingWindowCounter counter = RollingWindowCounter.perSecond(5);
        assertThrows(IllegalArgumentException.class, () -> counter.record(-1));
        assertThrows(IllegalArgumentException.class, () -> counter.isSpike(0.5));
    }
}
