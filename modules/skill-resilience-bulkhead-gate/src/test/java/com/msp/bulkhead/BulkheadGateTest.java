package com.msp.bulkhead;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class BulkheadGateTest {

    @Test
    @DisplayName("Should execute supplier and runnable successfully within limits")
    void testBasicExecution() {
        BulkheadGate gate = new BulkheadGate(2);

        String result = gate.executeSupplier(() -> "hello bulkhead");
        assertEquals("hello bulkhead", result);

        AtomicBoolean executed = new AtomicBoolean(false);
        gate.executeRunnable(() -> executed.set(true));
        assertTrue(executed.get());

        assertEquals(2, gate.getAvailablePermits());
        assertEquals(0, gate.getQueueDepth());
    }

    @Test
    @DisplayName("Should release permit when supplier throws user exception")
    void testReleaseOnException() {
        BulkheadGate gate = new BulkheadGate(1);

        assertThrows(IllegalStateException.class, () -> gate.executeSupplier(() -> {
            throw new IllegalStateException("Simulated user error");
        }));

        // Permit must be restored
        assertEquals(1, gate.getAvailablePermits());
        String ok = gate.executeSupplier(() -> "recovered");
        assertEquals("recovered", ok);
    }

    @Test
    @DisplayName("Should reject execution immediately when bulkhead permits are saturated and queue is 0")
    void testSaturationWithoutQueue() throws InterruptedException {
        BulkheadGate gate = new BulkheadGate(1);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(1);

        Thread thread = new Thread(() -> {
            gate.executeRunnable(() -> {
                startLatch.countDown();
                try {
                    finishLatch.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException ignored) {}
            });
        });
        thread.start();

        assertTrue(startLatch.await(2, TimeUnit.SECONDS));

        // Now permit is held by background thread
        assertEquals(0, gate.getAvailablePermits());
        assertFalse(gate.tryAcquire());

        // Any further execution should throw BulkheadFullException
        assertThrows(BulkheadFullException.class, () -> gate.executeRunnable(() -> {}));

        finishLatch.countDown();
        thread.join(2000);
        assertEquals(1, gate.getAvailablePermits());
    }

    @Test
    @DisplayName("Should queue callers and execute them once permits become available")
    void testQueueDrain() throws Exception {
        BulkheadConfig config = BulkheadConfig.of(1, 2, Duration.ofSeconds(2));
        BulkheadGate gate = new BulkheadGate(config);

        CountDownLatch holderStarted = new CountDownLatch(1);
        CountDownLatch holderRelease = new CountDownLatch(1);

        ExecutorService executor = Executors.newFixedThreadPool(3);
        try {
            // First task occupies the only permit
            Future<?> f1 = executor.submit(() -> gate.executeRunnable(() -> {
                holderStarted.countDown();
                try {
                    holderRelease.await();
                } catch (InterruptedException ignored) {}
            }));

            assertTrue(holderStarted.await(2, TimeUnit.SECONDS));

            // Second task will wait in queue
            Future<String> f2 = executor.submit(() -> gate.executeSupplier(() -> "queued-task-ok"));

            // Allow brief moment for f2 to enter wait queue
            Thread.sleep(50);
            BulkheadMetrics metrics = gate.getMetrics();
            assertEquals(0, metrics.availablePermits());
            assertEquals(1, metrics.currentConcurrentCalls());
            assertEquals(1, metrics.queueDepth());

            // Release first task
            holderRelease.countDown();
            f1.get(2, TimeUnit.SECONDS);

            // Second task must succeed
            assertEquals("queued-task-ok", f2.get(2, TimeUnit.SECONDS));
            assertEquals(1, gate.getAvailablePermits());
            assertEquals(0, gate.getQueueDepth());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("Should reject immediately when wait queue is exceeded")
    void testWaitQueueOverflow() throws Exception {
        // max 1 call, max 1 waiter
        BulkheadConfig config = BulkheadConfig.of(1, 1, Duration.ofSeconds(3));
        BulkheadGate gate = new BulkheadGate(config);

        CountDownLatch task1Started = new CountDownLatch(1);
        CountDownLatch releaseAll = new CountDownLatch(1);

        ExecutorService executor = Executors.newFixedThreadPool(3);
        try {
            // Task 1: acquires permit
            executor.submit(() -> gate.executeRunnable(() -> {
                task1Started.countDown();
                try {
                    releaseAll.await();
                } catch (InterruptedException ignored) {}
            }));
            assertTrue(task1Started.await(2, TimeUnit.SECONDS));

            // Task 2: enters wait queue (1/1)
            executor.submit(() -> gate.executeRunnable(() -> {
                try {
                    releaseAll.await();
                } catch (InterruptedException ignored) {}
            }));
            Thread.sleep(50);

            // Task 3: queue is full (1/1) -> BulkheadFullException
            assertThrows(BulkheadFullException.class, () -> gate.executeSupplier(() -> "rejected"));
        } finally {
            releaseAll.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("Should throw BulkheadFullException when wait timeout expires")
    void testWaitTimeoutExpiration() throws Exception {
        BulkheadConfig config = BulkheadConfig.of(1, 1, Duration.ofMillis(80));
        BulkheadGate gate = new BulkheadGate(config);

        CountDownLatch task1Started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            executor.submit(() -> gate.executeRunnable(() -> {
                task1Started.countDown();
                try {
                    release.await();
                } catch (InterruptedException ignored) {}
            }));
            assertTrue(task1Started.await(2, TimeUnit.SECONDS));

            // Waiter waits for 80ms, but task 1 does not finish in time
            assertThrows(BulkheadFullException.class, () -> gate.executeSupplier(() -> "should-timeout"));
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }
}
