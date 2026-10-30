package com.msp.cronoverlap;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class CronOverlapGuardTest {

    @Test
    @DisplayName("Normal execution executes task and records statistics")
    void testNormalExecution() {
        CronOverlapGuard guard = CronOverlapGuard.of("daily-settlement");
        AtomicBoolean ran = new AtomicBoolean(false);

        ExecutionResult<Void> res = guard.run(() -> ran.set(true));
        assertTrue(res.wasExecuted());
        assertTrue(ran.get());
        assertEquals(1, guard.executedCount());
        assertEquals(0, guard.skippedCount());
    }

    @Test
    @DisplayName("Concurrent execution is skipped while previous task is running")
    void testOverlapSkip() throws Exception {
        CronOverlapGuard guard = CronOverlapGuard.of("hourly-sync");

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(1);

        Thread longRunningTask = new Thread(() -> {
            guard.run(() -> {
                startLatch.countDown();
                try {
                    finishLatch.await();
                } catch (InterruptedException ignored) {}
            });
        });

        longRunningTask.start();
        startLatch.await(); // ensure 1st task is running

        assertTrue(guard.isRunning());

        // 2nd execution attempt during 1st running
        ExecutionResult<String> res2 = guard.call(() -> "Second task");
        assertTrue(res2.wasSkipped());
        assertEquals(1, guard.skippedCount());

        // Release 1st task
        finishLatch.countDown();
        longRunningTask.join();

        assertFalse(guard.isRunning());
        assertEquals(1, guard.executedCount());
        assertEquals(1, guard.skippedCount());
    }
}
