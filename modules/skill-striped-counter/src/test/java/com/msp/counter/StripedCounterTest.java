package com.msp.counter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("StripedCounter Unit Tests")
class StripedCounterTest {

    @Test
    @DisplayName("Basic single-threaded operations (increment, decrement, add, sum, reset)")
    void testBasicOperations() {
        StripedCounter counter = new StripedCounter(8);
        assertEquals(0, counter.sum());
        assertEquals(0, counter.longValue());

        counter.increment();
        assertEquals(1, counter.sum());

        counter.increment(5);
        assertEquals(6, counter.sum());

        counter.decrement();
        assertEquals(5, counter.sum());

        counter.decrement(3);
        assertEquals(2, counter.sum());

        counter.add(10);
        assertEquals(12, counter.sum());

        assertEquals(12, counter.intValue());
        assertEquals(12.0, counter.doubleValue(), 0.001);
        assertEquals(12.0f, counter.floatValue(), 0.001f);
        assertEquals("12", counter.toString());

        long previous = counter.sumThenReset();
        assertEquals(12, previous);
        assertEquals(0, counter.sum());

        counter.increment();
        counter.reset();
        assertEquals(0, counter.sum());
    }

    @Test
    @DisplayName("Stripe count normalization and boundary conditions")
    void testStripeCounts() {
        StripedCounter defaultCounter = new StripedCounter();
        assertTrue(defaultCounter.getStripeCount() >= 4);

        StripedCounter customCounter = new StripedCounter(5);
        assertEquals(8, customCounter.getStripeCount()); // rounds up to 8

        StripedCounter exactPow2 = new StripedCounter(16);
        assertEquals(16, exactPow2.getStripeCount());

        assertThrows(IllegalArgumentException.class, () -> new StripedCounter(0));
        assertThrows(IllegalArgumentException.class, () -> new StripedCounter(-1));
    }

    @Test
    @DisplayName("High contention multi-threaded test with 100 threads")
    void testHighContentionMultiThreaded() throws InterruptedException {
        int threadCount = 100;
        int incrementsPerThread = 10_000;
        long expectedTotal = (long) threadCount * incrementsPerThread;

        StripedCounter counter = new StripedCounter(16);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < incrementsPerThread; j++) {
                        counter.increment();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = endLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "All threads should finish within timeout");
        assertEquals(expectedTotal, counter.sum());
    }

    @Test
    @DisplayName("High contention mixed increments and decrements")
    void testMixedIncrementsAndDecrements() throws InterruptedException {
        int threadCount = 50;
        int operationsPerThread = 5_000;

        StripedCounter counter = new StripedCounter(8);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final boolean isEven = (i % 2 == 0);
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < operationsPerThread; j++) {
                        if (isEven) {
                            counter.increment();
                        } else {
                            counter.decrement();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = endLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed);
        // 25 even threads (+125,000), 25 odd threads (-125,000) -> sum should be exactly 0
        assertEquals(0, counter.sum());
    }
}
