package com.msp.stats;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class PercentileCalculatorTest {

    @Nested
    @DisplayName("PercentileCalculator Tests")
    class CalculatorTests {

        @Test
        @DisplayName("Empty calculator throws NoSuchElementException")
        void testEmptyCalculator() {
            PercentileCalculator calc = new PercentileCalculator();
            assertTrue(calc.isEmpty());
            assertEquals(0, calc.getCount());

            assertThrows(NoSuchElementException.class, calc::getP50);
            assertThrows(NoSuchElementException.class, calc::getMin);
            assertThrows(NoSuchElementException.class, calc::getMax);
            assertThrows(NoSuchElementException.class, calc::getMean);
            assertThrows(NoSuchElementException.class, calc::getIQR);
        }

        @Test
        @DisplayName("Single element dataset produces identical percentiles and 0 variance")
        void testSingleElement() {
            PercentileCalculator calc = new PercentileCalculator();
            calc.add(42.0);

            assertFalse(calc.isEmpty());
            assertEquals(1, calc.getCount());
            assertEquals(42.0, calc.getMin());
            assertEquals(42.0, calc.getMax());
            assertEquals(42.0, calc.getMean());
            assertEquals(42.0, calc.getP50());
            assertEquals(42.0, calc.getP90());
            assertEquals(42.0, calc.getP99());
            assertEquals(0.0, calc.getVariance());
            assertEquals(0.0, calc.getStandardDeviation());
            assertEquals(0.0, calc.getIQR());
        }

        @Test
        @DisplayName("Invalid percentile parameters throw IllegalArgumentException")
        void testInvalidPercentileArg() {
            PercentileCalculator calc = new PercentileCalculator();
            calc.add(10.0);
            calc.add(20.0);

            assertThrows(IllegalArgumentException.class, () -> calc.getPercentile(-1.0));
            assertThrows(IllegalArgumentException.class, () -> calc.getPercentile(100.1));
            assertThrows(IllegalArgumentException.class, () -> calc.getPercentile(Double.NaN));
        }

        @Test
        @DisplayName("Sorted and unsorted 1 to 100 produce identical percentiles")
        void test1To100Percentiles() {
            List<Double> list = new ArrayList<>();
            for (int i = 1; i <= 100; i++) {
                list.add((double) i);
            }

            PercentileCalculator sortedCalc = new PercentileCalculator(list);

            Collections.shuffle(list, new Random(42));
            PercentileCalculator unsortedCalc = new PercentileCalculator(list);

            assertEquals(100, sortedCalc.getCount());
            assertEquals(100, unsortedCalc.getCount());

            assertEquals(1.0, sortedCalc.getMin());
            assertEquals(100.0, sortedCalc.getMax());
            assertEquals(50.5, sortedCalc.getMean(), 0.001);

            // In 1..100 (size 100, rank = 99 * p / 100 + 0):
            // p0 -> 1.0
            // p50 -> index 49.5 -> 50 + 0.5 * (51 - 50) = 50.5
            // p100 -> 100.0
            assertEquals(1.0, sortedCalc.getPercentile(0.0));
            assertEquals(100.0, sortedCalc.getPercentile(100.0));
            assertEquals(50.5, sortedCalc.getP50());
            assertEquals(sortedCalc.getP50(), unsortedCalc.getP50());

            // Check p90, p95, p99, IQR
            assertEquals(sortedCalc.getP90(), unsortedCalc.getP90(), 0.0001);
            assertEquals(sortedCalc.getP95(), unsortedCalc.getP95(), 0.0001);
            assertEquals(sortedCalc.getP99(), unsortedCalc.getP99(), 0.0001);
            assertEquals(sortedCalc.getIQR(), unsortedCalc.getIQR(), 0.0001);
        }

        @Test
        @DisplayName("Known 5-number dataset verification")
        void testFiveNumberDataset() {
            // Data: 10, 20, 30, 40, 50
            // N = 5.
            // p0: index 0 -> 10
            // p25: index 4 * 0.25 = 1.0 -> 20
            // p50: index 4 * 0.50 = 2.0 -> 30
            // p75: index 4 * 0.75 = 3.0 -> 40
            // p100: index 4 * 1.00 = 4.0 -> 50
            // IQR = 40 - 20 = 20
            PercentileCalculator calc = new PercentileCalculator();
            calc.addAll(10.0, 20.0, 30.0, 40.0, 50.0);

            assertEquals(10.0, calc.getMin());
            assertEquals(50.0, calc.getMax());
            assertEquals(30.0, calc.getMean());
            assertEquals(30.0, calc.getP50());
            assertEquals(20.0, calc.getPercentile(25.0));
            assertEquals(40.0, calc.getPercentile(75.0));
            assertEquals(20.0, calc.getIQR());

            // Variance: sum((x - 30)^2) / 4 = (400 + 100 + 0 + 100 + 400) / 4 = 1000 / 4 = 250
            assertEquals(250.0, calc.getVariance(), 0.0001);
            assertEquals(Math.sqrt(250.0), calc.getStandardDeviation(), 0.0001);
        }

        @Test
        @DisplayName("Reset clears all data")
        void testReset() {
            PercentileCalculator calc = new PercentileCalculator();
            calc.addAll(1.0, 2.0, 3.0);
            assertFalse(calc.isEmpty());

            calc.reset();
            assertTrue(calc.isEmpty());
            assertEquals(0, calc.getCount());
            assertThrows(NoSuchElementException.class, calc::getP50);
        }

        @Test
        @DisplayName("Concurrent additions maintain consistency")
        void testConcurrency() throws InterruptedException {
            PercentileCalculator calc = new PercentileCalculator();
            int threads = 8;
            int itemsPerThread = 1000;
            ExecutorService executor = Executors.newFixedThreadPool(threads);
            CountDownLatch latch = new CountDownLatch(threads);

            for (int t = 0; t < threads; t++) {
                executor.submit(() -> {
                    try {
                        for (int i = 0; i < itemsPerThread; i++) {
                            calc.add(i);
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await();
            executor.shutdown();

            assertEquals(threads * itemsPerThread, calc.getCount());
            assertEquals(0.0, calc.getMin());
            assertEquals(itemsPerThread - 1, calc.getMax());
        }
    }

    @Nested
    @DisplayName("Histogram Tests")
    class HistogramTests {

        @Test
        @DisplayName("Uniform distribution distributes evenly into buckets")
        void testUniformDistribution() {
            List<Double> values = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                values.add((double) i); // 0 to 99
            }

            Histogram hist = Histogram.of(values, 10);
            assertEquals(0.0, hist.getMin());
            assertEquals(99.0, hist.getMax());
            assertEquals(10, hist.getBuckets().size());
            assertEquals(100, hist.getTotalCount());

            // 10 buckets for range [0, 99]: width = 9.9
            long totalBucketCount = 0;
            for (Bucket b : hist.getBuckets()) {
                totalBucketCount += b.count();
                assertTrue(b.count() > 0);
            }
            assertEquals(100, totalBucketCount);

            // Bucket finding
            assertTrue(hist.findBucket(0.0).isPresent());
            assertTrue(hist.findBucket(99.0).isPresent());
            assertTrue(hist.findBucket(50.0).isPresent());
            assertFalse(hist.findBucket(-10.0).isPresent());
            assertFalse(hist.findBucket(200.0).isPresent());
        }

        @Test
        @DisplayName("Identical values collapse into a single bucket")
        void testSingleValueHistogram() {
            List<Double> values = List.of(5.0, 5.0, 5.0, 5.0);
            Histogram hist = Histogram.of(values, 5);

            assertEquals(1, hist.getBuckets().size());
            assertEquals(4, hist.getTotalCount());
            Bucket b = hist.getBuckets().get(0);
            assertEquals(5.0, b.lowerBound());
            assertEquals(5.0, b.upperBound());
            assertEquals(4, b.count());
            assertEquals(100.0, b.percentage());
        }

        @Test
        @DisplayName("Ascii rendering output")
        void testAsciiRendering() {
            List<Double> values = List.of(1.0, 2.0, 3.0, 10.0, 10.0, 10.0);
            Histogram hist = Histogram.of(values, 3);
            String ascii = hist.renderAscii(20);
            assertNotNull(ascii);
            assertTrue(ascii.contains("Histogram [Total: 6"));
        }
    }

    @Nested
    @DisplayName("ReservoirSampler Tests")
    class ReservoirSamplerTests {

        @Test
        @DisplayName("Stream smaller than capacity retains all items")
        void testSmallerThanCapacity() {
            ReservoirSampler sampler = new ReservoirSampler(10);
            sampler.addAll(1.0, 2.0, 3.0, 4.0, 5.0);

            assertEquals(10, sampler.getCapacity());
            assertEquals(5, sampler.getTotalSeen());
            assertEquals(5, sampler.getSampleSize());
            assertEquals(List.of(1.0, 2.0, 3.0, 4.0, 5.0), sampler.getSamples());
        }

        @Test
        @DisplayName("Stream larger than capacity bounds sample size to exact capacity")
        void testBoundToCapacity() {
            int capacity = 50;
            ReservoirSampler sampler = new ReservoirSampler(capacity, new Random(12345));

            for (int i = 0; i < 10_000; i++) {
                sampler.add(i);
            }

            assertEquals(10_000, sampler.getTotalSeen());
            assertEquals(capacity, sampler.getSampleSize());
            assertEquals(capacity, sampler.getSamples().size());

            // Converts to PercentileCalculator without errors
            PercentileCalculator calc = sampler.toPercentileCalculator();
            assertEquals(capacity, calc.getCount());
            assertTrue(calc.getP50() > 0 && calc.getP50() < 10_000);
        }

        @Test
        @DisplayName("Invalid capacity throws IllegalArgumentException")
        void testInvalidCapacity() {
            assertThrows(IllegalArgumentException.class, () -> new ReservoirSampler(0));
            assertThrows(IllegalArgumentException.class, () -> new ReservoirSampler(-5));
        }
    }
}
