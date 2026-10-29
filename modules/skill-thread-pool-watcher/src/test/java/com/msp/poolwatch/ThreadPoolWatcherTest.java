package com.msp.poolwatch;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ThreadPoolWatcherTest {

    private ThreadPoolExecutor executor;
    private ThreadPoolWatcher watcher;

    @AfterEach
    void tearDown() {
        if (watcher != null) {
            watcher.close();
        }
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("Normal snapshot captures accurate metrics")
    void snapshotMetricsAccurate() {
        // Pool with 2 threads, queue capacity 10
        executor = new ThreadPoolExecutor(2, 2, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(10));
        watcher = ThreadPoolWatcher.builder()
                .name("metrics-test-pool")
                .executor(executor)
                .build();

        PoolSnapshot snapshot = watcher.takeSnapshot();

        assertEquals("metrics-test-pool", snapshot.poolName());
        assertEquals(2, snapshot.corePoolSize());
        assertEquals(2, snapshot.maximumPoolSize());
        assertEquals(0, snapshot.queueSize());
        assertEquals(10, snapshot.queueRemainingCapacity());
        assertEquals(0.0, snapshot.queueSaturationRatio());
        assertEquals(0.0, snapshot.activeThreadRatio());
        assertEquals(0, snapshot.rejectedCount());
    }

    @Test
    @DisplayName("Queue saturation triggers QUEUE_SATURATION alert")
    void queueSaturationAlertTriggered() throws InterruptedException {
        // Pool: 1 thread, queue: 5 items
        executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(5));

        List<AlertReason> alerts = new ArrayList<>();
        CountDownLatch activeTaskLatch = new CountDownLatch(1);

        watcher = ThreadPoolWatcher.builder()
                .name("saturation-pool")
                .executor(executor)
                .thresholds(new ThresholdConfig(0.60, 1.0)) // 60% queue saturation triggers alert
                .alertListener((snapshot, reason, current, threshold) -> {
                    alerts.add(reason);
                })
                .build();

        // Submit 1 task to block the worker thread
        executor.submit(() -> {
            try {
                activeTaskLatch.await();
            } catch (InterruptedException ignored) {}
        });

        // Submit 4 tasks into queue of capacity 5 (4/5 = 80% saturation > 60%)
        for (int i = 0; i < 4; i++) {
            executor.submit(() -> {});
        }

        PoolSnapshot snapshot = watcher.checkOnce();

        assertEquals(4, snapshot.queueSize());
        assertEquals(1, snapshot.queueRemainingCapacity());
        assertEquals(0.80, snapshot.queueSaturationRatio(), 0.001);
        assertTrue(snapshot.isQueueSaturated(0.60));
        assertTrue(alerts.contains(AlertReason.QUEUE_SATURATION));

        activeTaskLatch.countDown();
    }

    @Test
    @DisplayName("Active worker saturation triggers ACTIVE_WORKER_SATURATION alert")
    void activeWorkerSaturationAlert() throws InterruptedException {
        executor = new ThreadPoolExecutor(2, 2, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(10));
        List<AlertReason> alerts = new ArrayList<>();
        CountDownLatch holdLatch = new CountDownLatch(1);

        watcher = ThreadPoolWatcher.builder()
                .name("worker-alert-pool")
                .executor(executor)
                .thresholds(new ThresholdConfig(1.0, 0.90)) // 90% worker utilization triggers alert
                .alertListener((snap, reason, curr, thresh) -> alerts.add(reason))
                .build();

        // Occupy both threads (2/2 = 100% active > 90%)
        executor.submit(() -> {
            try {
                holdLatch.await();
            } catch (InterruptedException ignored) {}
        });
        executor.submit(() -> {
            try {
                holdLatch.await();
            } catch (InterruptedException ignored) {}
        });

        Thread.sleep(50); // Give threads moment to become active
        watcher.checkOnce();

        assertTrue(alerts.contains(AlertReason.ACTIVE_WORKER_SATURATION));
        holdLatch.countDown();
    }

    @Test
    @DisplayName("Task rejection increments rejectionCount and triggers TASK_REJECTION alert")
    void taskRejectionTrackedAndAlerted() {
        // Pool: 1 thread, queue: 1
        executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(1));
        List<AlertReason> alerts = new ArrayList<>();

        watcher = ThreadPoolWatcher.builder()
                .name("rejection-pool")
                .executor(executor)
                .alertListener((snap, reason, curr, thresh) -> alerts.add(reason))
                .autoWrapRejectedHandler(true)
                .build();

        CountDownLatch blocker = new CountDownLatch(1);

        // Fill thread + queue
        executor.submit(() -> {
            try {
                blocker.await();
            } catch (InterruptedException ignored) {}
        });
        executor.submit(() -> {});

        // 3rd task will be rejected
        assertThrows(RejectedExecutionException.class, () -> executor.submit(() -> {}));

        PoolSnapshot snapshot = watcher.takeSnapshot();
        assertEquals(1, snapshot.rejectedCount());
        assertTrue(alerts.contains(AlertReason.TASK_REJECTION));

        blocker.countDown();
    }

    @Test
    @DisplayName("Periodic monitoring starts and stops cleanly")
    void periodicMonitoring() throws InterruptedException {
        executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(10));
        AtomicInteger checkCount = new AtomicInteger(0);

        watcher = ThreadPoolWatcher.builder()
                .name("periodic-pool")
                .executor(executor)
                .alertListener((s, r, c, t) -> {})
                .thresholds(new ThresholdConfig(0.0, 0.0)) // Trigger on every check
                .alertListener((s, r, c, t) -> checkCount.incrementAndGet())
                .build();

        watcher.start(Duration.ofMillis(30));
        assertTrue(watcher.isRunning());

        Thread.sleep(110);
        watcher.stop();
        assertFalse(watcher.isRunning());

        int count = checkCount.get();
        assertTrue(count >= 2, "Periodic check should have run at least twice, was " + count);

        // Subsequent sleep should not increment further
        Thread.sleep(80);
        assertEquals(count, checkCount.get(), "Count should remain frozen after stop()");
    }
}
