package com.msp.leakdetect;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class MemoryLeakDetectorTest {

    @Test
    @DisplayName("Monotonic growth is detected after consecutive sample increases")
    void testMonotonicGrowthDetection() {
        try (MemoryLeakDetector detector = new MemoryLeakDetector()) {
            List<String> list = new ArrayList<>();
            DetectionConfig config = DetectionConfig.builder()
                    .consecutiveGrowthThreshold(3)
                    .velocityThresholdPerSecond(1000.0) // high to isolate monotonic
                    .maxCapacityThreshold(10_000)
                    .build();

            detector.watch("testList", list, config);

            // Sample 1: initial size = 0
            List<LeakWarning> w1 = detector.sample();
            assertTrue(w1.isEmpty());

            // Sample 2: size = 1 (1st increase)
            list.add("item1");
            List<LeakWarning> w2 = detector.sample();
            assertTrue(w2.isEmpty());

            // Sample 3: size = 2 (2nd increase)
            list.add("item2");
            List<LeakWarning> w3 = detector.sample();
            assertTrue(w3.isEmpty());

            // Sample 4: size = 3 (3rd increase -> triggers threshold 3)
            list.add("item3");
            List<LeakWarning> w4 = detector.sample();
            assertEquals(1, w4.size());
            assertEquals(WarningReason.MONOTONIC_GROWTH, w4.get(0).reason());
            assertEquals("testList", w4.get(0).targetName());
            assertEquals(3, w4.get(0).currentSize());
        }
    }

    @Test
    @DisplayName("Shrinking size resets consecutive growth counter")
    void testShrinkingResetsCounter() {
        try (MemoryLeakDetector detector = new MemoryLeakDetector()) {
            AtomicInteger size = new AtomicInteger(10);
            DetectionConfig config = DetectionConfig.builder()
                    .consecutiveGrowthThreshold(3)
                    .velocityThresholdPerSecond(1000.0)
                    .build();

            detector.watch("dynamicCounter", size::get, config);

            detector.sample(); // 10
            size.set(11);
            detector.sample(); // 11 (growth 1)
            size.set(12);
            detector.sample(); // 12 (growth 2)

            // Shrink
            size.set(5);
            detector.sample(); // 5 (reset)

            TargetStats stats = detector.getStats("dynamicCounter").orElseThrow();
            assertEquals(0, stats.consecutiveGrowthCount());
            assertEquals(5, stats.currentSize());
        }
    }

    @Test
    @DisplayName("Absolute capacity threshold triggers alert immediately")
    void testCapacityExceeded() {
        try (MemoryLeakDetector detector = new MemoryLeakDetector()) {
            Map<String, String> cache = new HashMap<>();
            DetectionConfig config = DetectionConfig.builder()
                    .maxCapacityThreshold(5)
                    .consecutiveGrowthThreshold(10)
                    .velocityThresholdPerSecond(1000.0)
                    .build();

            detector.watch("sessionCache", cache, config);

            detector.sample(); // initial empty

            for (int i = 0; i < 6; i++) {
                cache.put("key" + i, "val" + i);
            }

            List<LeakWarning> warnings = detector.sample();
            boolean hasCapacityWarning = warnings.stream()
                    .anyMatch(w -> w.reason() == WarningReason.CAPACITY_LIMIT_EXCEEDED);
            assertTrue(hasCapacityWarning);
        }
    }

    @Test
    @DisplayName("Listener receives warnings during sampling")
    void testListenerCallback() {
        try (MemoryLeakDetector detector = new MemoryLeakDetector()) {
            List<LeakWarning> captured = new CopyOnWriteArrayList<>();
            detector.addListener(captured::add);

            AtomicInteger counter = new AtomicInteger(100);
            DetectionConfig config = DetectionConfig.builder()
                    .maxCapacityThreshold(150)
                    .build();

            detector.watch("counter", counter::get, config);
            detector.sample();

            counter.set(200);
            detector.sample();

            assertFalse(captured.isEmpty());
            assertEquals(WarningReason.CAPACITY_LIMIT_EXCEEDED, captured.get(0).reason());
        }
    }

    @Test
    @DisplayName("Periodic scheduled sampling runs in background")
    void testScheduledSampling() throws InterruptedException {
        try (MemoryLeakDetector detector = new MemoryLeakDetector()) {
            CountDownLatch latch = new CountDownLatch(1);
            detector.addListener(w -> latch.countDown());

            AtomicInteger value = new AtomicInteger(0);
            DetectionConfig config = DetectionConfig.builder()
                    .maxCapacityThreshold(5)
                    .build();

            detector.watch("bgTarget", value::get, config);
            detector.start(Duration.ofMillis(20));

            // Set above threshold
            value.set(10);

            boolean received = latch.await(2, TimeUnit.SECONDS);
            assertTrue(received, "Scheduled background sampler should detect threshold and trigger latch");
        }
    }
}
