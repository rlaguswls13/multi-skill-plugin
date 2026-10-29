package com.msp.counter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SlidingWindowCounter Unit Tests")
class SlidingWindowCounterTest {

    @Test
    @DisplayName("Validation and factory methods configuration")
    void testConfigurationAndValidation() {
        SlidingWindowCounter perSec = SlidingWindowCounter.perSecond(10);
        assertEquals(Duration.ofSeconds(1), perSec.getWindowDuration());
        assertEquals(10, perSec.getBucketCount());
        assertEquals(Duration.ofMillis(100), perSec.getBucketDuration());

        SlidingWindowCounter perMin = SlidingWindowCounter.perMinute(60);
        assertEquals(Duration.ofMinutes(1), perMin.getWindowDuration());
        assertEquals(60, perMin.getBucketCount());
        assertEquals(Duration.ofSeconds(1), perMin.getBucketDuration());

        assertThrows(NullPointerException.class, () -> new SlidingWindowCounter(null, 10));
        assertThrows(IllegalArgumentException.class, () -> new SlidingWindowCounter(Duration.ZERO, 10));
        assertThrows(IllegalArgumentException.class, () -> new SlidingWindowCounter(Duration.ofSeconds(1), 0));
        assertThrows(IllegalArgumentException.class, () -> perSec.record(-1));
    }

    @Test
    @DisplayName("Accurate rate and sliding window expiration over deterministic time")
    void testSlidingWindowRateAccuracyOverTime() {
        // Window: 1000ms (1 second), 10 buckets of 100ms
        AtomicLong clock = new AtomicLong(10_000L); // start at 10,000 ms
        SlidingWindowCounter counter = SlidingWindowCounter.of(Duration.ofSeconds(1), 10, clock::get);

        assertEquals(0, counter.getTotalCount());
        assertEquals(0.0, counter.getRatePerSecond());

        // At t=10,050 (bucket 100), record 20 events
        clock.set(10_050L);
        counter.record(20);
        assertEquals(20, counter.getTotalCount());
        assertEquals(20.0, counter.getRatePerSecond());
        assertEquals(1200.0, counter.getRatePerMinute());

        // At t=10,350 (bucket 103), record 30 events
        clock.set(10_350L);
        counter.record(30);
        assertEquals(50, counter.getTotalCount());
        assertEquals(50.0, counter.getRatePerSecond());

        // At t=10,850 (bucket 108), record 50 events
        clock.set(10_850L);
        counter.record(50);
        assertEquals(100, counter.getTotalCount());
        assertEquals(100.0, counter.getRatePerSecond());

        // At t=11,060 (bucket 110): bucket 100 (t=10,050) has expired!
        // Valid range is buckets [110 - 10 + 1, 110] = [101, 110].
        // 20 events from bucket 100 should slide out. Remaining: 30 + 50 = 80 events.
        clock.set(11_060L);
        assertEquals(80, counter.getTotalCount());
        assertEquals(80.0, counter.getRatePerSecond());

        // At t=11,360 (bucket 113): bucket 103 (t=10,350) has expired!
        // Remaining: only 50 events.
        clock.set(11_360L);
        assertEquals(50, counter.getTotalCount());

        // At t=12,000 (bucket 120): all previous events have slid out!
        clock.set(12_000L);
        assertEquals(0, counter.getTotalCount());
        assertEquals(0.0, counter.getRatePerSecond());
    }

    @Test
    @DisplayName("Multi-threaded concurrent event recording")
    void testConcurrentRecording() throws InterruptedException {
        int threadCount = 20;
        int recordsPerThread = 1_000;
        SlidingWindowCounter counter = SlidingWindowCounter.perMinute(60);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < recordsPerThread; j++) {
                        counter.record();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = doneLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed);
        assertEquals((long) threadCount * recordsPerThread, counter.getTotalCount());

        counter.reset();
        assertEquals(0, counter.getTotalCount());
    }
}
