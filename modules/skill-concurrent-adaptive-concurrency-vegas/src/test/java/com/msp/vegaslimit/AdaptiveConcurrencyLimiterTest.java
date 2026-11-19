package com.msp.vegaslimit;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class AdaptiveConcurrencyLimiterTest {

    @Test
    void vegasIncreasesLimitOnLowRttAndCapsAtMaxLimit() {
        AtomicLong clock = new AtomicLong(1_000_000L);
        VegasAlgorithm vegas = VegasAlgorithm.builder()
                .initialLimit(10)
                .minLimit(2)
                .maxLimit(15)
                .alpha(3.0)
                .beta(6.0)
                .smoothing(1.0)
                .build();
        ConcurrencyLimiter limiter = new ConcurrencyLimiter(vegas, clock::get);

        // Simulate 10 fast requests (10ms each) -> queueSize = 0 <= alpha -> limit grows to maxLimit=15
        for (int i = 0; i < 10; i++) {
            ConcurrencyLimiter.Listener listener = limiter.tryAcquire().orElseThrow();
            clock.addAndGet(10_000_000L);
            listener.onSuccess();
        }

        assertEquals(15, limiter.currentLimit());
        assertEquals(0, limiter.inFlight());
        assertEquals(10_000_000L, limiter.snapshot().minRttNanos());
    }

    @Test
    void vegasDecreasesLimitWhenQueueGrowsBeyondBeta() {
        AtomicLong clock = new AtomicLong(0L);
        VegasAlgorithm vegas = VegasAlgorithm.builder()
                .initialLimit(20)
                .minLimit(5)
                .maxLimit(50)
                .alpha(3.0)
                .beta(6.0)
                .smoothing(1.0)
                .build();
        ConcurrencyLimiter limiter = new ConcurrencyLimiter(vegas, clock::get);

        // Establish baseline minRtt = 10ms
        ConcurrencyLimiter.Listener baseline = limiter.tryAcquire().orElseThrow();
        clock.addAndGet(10_000_000L);
        baseline.onSuccess();
        assertEquals(21, limiter.currentLimit());

        // Now latency jumps to 40ms -> queueSize = 21 * (1 - 10/40) = 15.75 >= beta(6) -> limit decreases
        int before = limiter.currentLimit();
        for (int i = 0; i < 5; i++) {
            ConcurrencyLimiter.Listener listener = limiter.tryAcquire().orElseThrow();
            clock.addAndGet(40_000_000L);
            listener.onSuccess();
        }
        assertTrue(limiter.currentLimit() < before, "Limit should decrease under high queuing latency");
        assertTrue(limiter.snapshot().estimatedQueueSize() >= 6.0);
    }

    @Test
    void vegasDropsBackoffAndIgnoreDoesNotModifyLimit() {
        AtomicLong clock = new AtomicLong(0L);
        VegasAlgorithm vegas = VegasAlgorithm.builder()
                .initialLimit(20)
                .minLimit(2)
                .maxLimit(50)
                .backoffRatio(0.8)
                .smoothing(1.0)
                .build();
        ConcurrencyLimiter limiter = new ConcurrencyLimiter(vegas, clock::get);

        // onDropped reduces limit from 20 -> 16
        ConcurrencyLimiter.Listener dropped = limiter.tryAcquire().orElseThrow();
        clock.addAndGet(100_000_000L);
        dropped.onDropped();
        assertEquals(16, limiter.currentLimit());
        assertEquals(1, limiter.snapshot().totalDropped());

        // onIgnore releases slot without altering limit
        ConcurrencyLimiter.Listener ignored = limiter.tryAcquire().orElseThrow();
        assertEquals(1, limiter.inFlight());
        clock.addAndGet(500_000_000L);
        ignored.onIgnore();
        assertEquals(0, limiter.inFlight());
        assertEquals(16, limiter.currentLimit());

        // Calling listener multiple times is idempotent
        ignored.onIgnore();
        ignored.onSuccess();
        assertEquals(0, limiter.inFlight());
        assertEquals(16, limiter.currentLimit());
    }

    @Test
    void rejectsRequestsWhenInFlightReachesCurrentLimit() {
        AtomicLong clock = new AtomicLong(0L);
        VegasAlgorithm vegas = VegasAlgorithm.builder()
                .initialLimit(3)
                .minLimit(1)
                .maxLimit(10)
                .build();
        ConcurrencyLimiter limiter = new ConcurrencyLimiter(vegas, clock::get);

        List<ConcurrencyLimiter.Listener> active = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            active.add(limiter.tryAcquire().orElseThrow());
        }
        assertEquals(3, limiter.inFlight());
        assertEquals(Optional.empty(), limiter.tryAcquire());
        assertEquals(1, limiter.snapshot().totalRejected());

        // Release one slot -> next acquire succeeds
        active.get(0).onIgnore();
        assertEquals(2, limiter.inFlight());
        assertTrue(limiter.tryAcquire().isPresent());
    }

    @Test
    void gradientAlgorithmScalesDownOnLatencySpikeAndRecovers() {
        AtomicLong clock = new AtomicLong(0L);
        GradientAlgorithm gradient = GradientAlgorithm.builder()
                .initialLimit(20)
                .minLimit(4)
                .maxLimit(40)
                .rttTolerance(1.5)
                .smoothing(0.5)
                .longWindow(50)
                .build();
        ConcurrencyLimiter limiter = new ConcurrencyLimiter(gradient, clock::get);

        // Warm up with stable 10ms RTT -> limit grows
        for (int i = 0; i < 10; i++) {
            ConcurrencyLimiter.Listener l = limiter.tryAcquire().orElseThrow();
            clock.addAndGet(10_000_000L);
            l.onSuccess();
        }
        int peakLimit = limiter.currentLimit();
        assertTrue(peakLimit > 20, "Gradient limit should grow under stable latency, got " + peakLimit);

        // Sudden 50ms latency spike -> gradient = 1.5 * 10 / 50 = 0.3 -> clamped to 0.5 -> limit shrinks
        for (int i = 0; i < 5; i++) {
            ConcurrencyLimiter.Listener l = limiter.tryAcquire().orElseThrow();
            clock.addAndGet(50_000_000L);
            l.onSuccess();
        }
        int reducedLimit = limiter.currentLimit();
        assertTrue(reducedLimit < peakLimit, "Gradient limit should drop on latency spike");

        // Recovery back to 10ms RTT -> limit increases again
        for (int i = 0; i < 10; i++) {
            ConcurrencyLimiter.Listener l = limiter.tryAcquire().orElseThrow();
            clock.addAndGet(10_000_000L);
            l.onSuccess();
        }
        assertTrue(limiter.currentLimit() > reducedLimit, "Gradient limit should recover when latency normalizes");
    }

    @Test
    void concurrentAcquireAndReleaseIsThreadSafe() throws InterruptedException {
        VegasAlgorithm vegas = VegasAlgorithm.builder()
                .initialLimit(8)
                .minLimit(2)
                .maxLimit(32)
                .build();
        ConcurrencyLimiter limiter = new ConcurrencyLimiter(vegas);

        int threads = 8;
        int iterations = 200;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch done = new CountDownLatch(threads);

        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                try {
                    for (int i = 0; i < iterations; i++) {
                        limiter.tryAcquire().ifPresent(ConcurrencyLimiter.Listener::onSuccess);
                    }
                } finally {
                    done.countDown();
                }
            });
        }

        assertTrue(done.await(10, TimeUnit.SECONDS));
        pool.shutdownNow();
        assertEquals(0, limiter.inFlight(), "All permits must be released cleanly");
        assertTrue(limiter.currentLimit() >= 2 && limiter.currentLimit() <= 32);
    }
}
