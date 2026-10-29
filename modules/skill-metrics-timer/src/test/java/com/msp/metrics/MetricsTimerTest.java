package com.msp.metrics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class MetricsTimerTest {

    private MetricsTimer timer;

    @BeforeEach
    void setUp() {
        timer = MetricsTimer.create();
    }

    @Test
    void testLatencyTimingRunnableAndSupplier() {
        timer.time("runnable.task", () -> {
            try {
                Thread.sleep(30);
            } catch (InterruptedException ignored) {
            }
        });

        String result = timer.time("supplier.task", () -> {
            try {
                Thread.sleep(20);
            } catch (InterruptedException ignored) {
            }
            return "SUCCESS";
        });

        assertEquals("SUCCESS", result);

        MetricSnapshot runSnap = timer.snapshot("runnable.task").orElseThrow();
        assertEquals(1, runSnap.count());
        assertTrue(runSnap.minMillis() >= 25.0, "Measured runnable latency should be >= 25ms");

        MetricSnapshot suppSnap = timer.snapshot("supplier.task").orElseThrow();
        assertEquals(1, suppSnap.count());
        assertTrue(suppSnap.minMillis() >= 15.0, "Measured supplier latency should be >= 15ms");
    }

    @Test
    void testTryWithResourcesTimerSample() throws InterruptedException {
        try (TimerSample sample = timer.start("sample.task")) {
            Thread.sleep(25);
            assertTrue(sample.elapsedNanos() > 0);
        }

        MetricSnapshot snap = timer.snapshot("sample.task").orElseThrow();
        assertEquals(1, snap.count());
        assertTrue(snap.minMillis() >= 20.0);
    }

    @Test
    void testPercentileCalculationAccuracy() {
        MetricsTimer customTimer = MetricsTimer.create(500);

        // Feed 1ms to 100ms in steps of 1ms
        for (int i = 1; i <= 100; i++) {
            customTimer.record("percentile.test", Duration.ofMillis(i));
        }

        MetricSnapshot snap = customTimer.snapshot("percentile.test").orElseThrow();
        assertEquals(100, snap.count());
        assertEquals(1.0, snap.minMillis(), 0.001);
        assertEquals(100.0, snap.maxMillis(), 0.001);
        assertEquals(50.5, snap.meanMillis(), 0.01);

        // p50 should be ~50.5ms
        assertEquals(50.5, snap.p50Millis(), 1.0);
        // p90 should be ~90.1ms
        assertEquals(90.1, snap.p90Millis(), 1.0);
        // p95 should be ~95.05ms
        assertEquals(95.05, snap.p95Millis(), 1.0);
        // p99 should be ~99.01ms
        assertEquals(99.01, snap.p99Millis(), 1.0);
    }

    @Test
    void testConcurrentMeasurement() throws InterruptedException {
        int threadCount = 20;
        int iterations = 100;
        CountDownLatch latch = new CountDownLatch(threadCount);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int t = 0; t < threadCount; t++) {
                executor.submit(() -> {
                    try {
                        for (int i = 0; i < iterations; i++) {
                            timer.record("concurrent.metric", Duration.ofNanos(1000 + i));
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }
            assertTrue(latch.await(5, TimeUnit.SECONDS));
        }

        MetricSnapshot snap = timer.snapshot("concurrent.metric").orElseThrow();
        assertEquals(threadCount * iterations, snap.count());
        assertTrue(snap.minNanos() >= 1000);
        assertTrue(snap.maxNanos() <= 1000 + iterations);
    }

    @Test
    void testTpsCalculation() {
        for (int i = 0; i < 50; i++) {
            timer.record("tps.test", Duration.ofMillis(1));
        }

        MetricSnapshot snap = timer.snapshot("tps.test").orElseThrow();
        assertEquals(50, snap.count());
        assertTrue(snap.tps() > 0.0, "TPS should be greater than zero");
    }

    @Test
    void testMetricsReportTableAndJson() {
        timer.record("api.users", Duration.ofMillis(12));
        timer.record("api.orders", Duration.ofMillis(45));

        MetricsReport report = timer.report();
        assertNotNull(report.generatedAt());
        assertEquals(2, report.snapshots().size());

        String table = report.toTable();
        assertTrue(table.contains("api.users"));
        assertTrue(table.contains("api.orders"));
        assertTrue(table.contains("Metric Name"));

        String json = report.toJson();
        assertTrue(json.contains("\"generatedAt\""));
        assertTrue(json.contains("\"name\":\"api.users\""));
        assertTrue(json.contains("\"p99Ms\""));
        assertTrue(json.contains("\"tps\""));
    }

    @Test
    void testMetricsFacade() {
        Metrics.reset();
        Metrics.time("facade.test", () -> {
            // fast operation
        });

        Optional<MetricSnapshot> snap = Metrics.snapshot("facade.test");
        assertTrue(snap.isPresent());
        assertEquals(1, snap.get().count());

        MetricsReport report = Metrics.report();
        assertFalse(report.snapshots().isEmpty());

        Metrics.reset();
        assertTrue(Metrics.snapshots().isEmpty());
    }
}
