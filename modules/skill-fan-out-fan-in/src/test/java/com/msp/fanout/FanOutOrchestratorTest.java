package com.msp.fanout;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FanOutOrchestrator Unit Tests")
class FanOutOrchestratorTest {

    @Test
    @DisplayName("Empty task list handles gracefully")
    void testEmptyTasks() {
        FanInResult<String> result = FanOutOrchestrator.<String>fanOut(List.of()).execute();
        assertNotNull(result);
        assertEquals(0, result.totalTasks());
        assertEquals(0, result.successCount());
        assertEquals(0, result.failureCount());
        assertTrue(result.isAllSuccessful());
        assertFalse(result.hasFailures());
    }

    @Test
    @DisplayName("Concurrent execution of 100 tasks on Virtual Threads")
    void testConcurrentExecutionOnVirtualThreads() {
        int taskCount = 100;
        AtomicBoolean allVirtual = new AtomicBoolean(true);
        List<Callable<Integer>> tasks = new ArrayList<>();

        for (int i = 0; i < taskCount; i++) {
            final int index = i;
            tasks.add(() -> {
                if (!Thread.currentThread().isVirtual()) {
                    allVirtual.set(false);
                }
                Thread.sleep(10);
                return index * 2;
            });
        }

        FanInResult<Integer> result = FanOutOrchestrator.fanOut(tasks)
                .namePrefix("test-vthread-")
                .execute();

        assertTrue(allVirtual.get(), "Tasks should run on Java 21 Virtual Threads");
        assertEquals(taskCount, result.totalTasks());
        assertEquals(taskCount, result.successCount());
        assertEquals(0, result.failureCount());
        assertTrue(result.isAllSuccessful());

        for (int i = 0; i < taskCount; i++) {
            assertEquals(i * 2, result.successfulResults().get(i));
        }
    }

    @Test
    @DisplayName("Partial failure aggregation preserves successes and maps failures")
    void testPartialFailureAggregation() {
        List<Callable<String>> tasks = List.of(
                () -> "Task 0 OK",
                () -> { throw new IllegalArgumentException("Invalid argument in Task 1"); },
                () -> "Task 2 OK",
                () -> { throw new IllegalStateException("Illegal state in Task 3"); },
                () -> "Task 4 OK"
        );

        FanInResult<String> result = FanOutOrchestrator.fanOut(tasks).execute();

        assertEquals(5, result.totalTasks());
        assertEquals(3, result.successCount());
        assertEquals(2, result.failureCount());
        assertFalse(result.isAllSuccessful());
        assertTrue(result.hasFailures());

        assertEquals(List.of("Task 0 OK", "Task 2 OK", "Task 4 OK"), result.successfulResults());

        assertEquals(2, result.failures().size());
        assertTrue(result.failureMap().containsKey(1));
        assertTrue(result.failureMap().containsKey(3));
        assertInstanceOf(IllegalArgumentException.class, result.failureMap().get(1));
        assertInstanceOf(IllegalStateException.class, result.failureMap().get(3));
    }

    @Test
    @DisplayName("Timeout handling cancels slow tasks and records TimeoutException")
    void testTimeoutHandling() {
        List<Callable<String>> tasks = List.of(
                () -> {
                    Thread.sleep(20);
                    return "Fast Task 0";
                },
                () -> {
                    Thread.sleep(2000); // Exceeds timeout
                    return "Slow Task 1";
                },
                () -> {
                    Thread.sleep(20);
                    return "Fast Task 2";
                }
        );

        long start = System.currentTimeMillis();
        FanInResult<String> result = FanOutOrchestrator.fanOut(tasks)
                .timeout(Duration.ofMillis(200))
                .execute();
        long elapsed = System.currentTimeMillis() - start;

        // Overall execution should terminate well before 2000ms
        assertTrue(elapsed < 1000, "Execution should be bounded by timeout");
        assertTrue(result.hasFailures());
        assertTrue(result.failureMap().containsKey(1));
        assertInstanceOf(TimeoutException.class, result.failureMap().get(1));
    }

    @Test
    @DisplayName("Fail-fast mode aborts remaining tasks upon failure")
    void testFailFastMode() {
        List<Callable<String>> tasks = List.of(
                () -> { throw new RuntimeException("Boom in task 0"); },
                () -> {
                    Thread.sleep(500);
                    return "Task 1";
                }
        );

        FanInResult<String> result = FanOutOrchestrator.fanOut(tasks)
                .failFast(true)
                .execute();

        assertFalse(result.isAllSuccessful());
        assertTrue(result.hasFailures());
    }

    @Test
    @DisplayName("Varargs factory convenience method")
    void testVarargsFanOut() {
        FanInResult<Integer> result = FanOutOrchestrator.fanOut(
                () -> 10,
                () -> 20,
                () -> 30
        ).execute();

        assertEquals(3, result.successCount());
        assertEquals(List.of(10, 20, 30), result.successfulResults());
    }
}
