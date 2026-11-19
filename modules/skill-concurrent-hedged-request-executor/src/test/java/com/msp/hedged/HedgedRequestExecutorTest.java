package com.msp.hedged;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class HedgedRequestExecutorTest {

    @Test
    void primaryWinsWhenCompletingBeforeHedgeDelay() throws Exception {
        try (HedgedRequestExecutor executor = HedgedRequestExecutor.builder()
                .hedgeDelay(Duration.ofMillis(200))
                .maxHedgedAttempts(2)
                .build()) {

            HedgedResult<String> result = executor.executeWithMetadata(attemptIndex -> {
                if (attemptIndex == 0) {
                    return "primary-fast";
                }
                Thread.sleep(500);
                return "hedge-" + attemptIndex;
            });

            assertEquals("primary-fast", result.value());
            assertTrue(result.isPrimaryWin());
            assertFalse(result.isHedgedWin());
            assertEquals(0, result.winningAttemptIndex());
            assertEquals(0, result.hedgesFiredForCall());

            HedgedMetrics metrics = executor.metrics();
            assertEquals(1, metrics.primaryWins());
            assertEquals(0, metrics.hedgedWins());
            assertEquals(0, metrics.totalHedgesFired());
            assertEquals(0, metrics.cancelledRedundantCalls());
        }
    }

    @Test
    void hedgedAttemptWinsAndCancelsSlowPrimary() throws Exception {
        CountDownLatch primaryCancelledLatch = new CountDownLatch(1);
        AtomicBoolean primaryInterrupted = new AtomicBoolean(false);

        try (HedgedRequestExecutor executor = HedgedRequestExecutor.builder()
                .hedgeDelay(Duration.ofMillis(35))
                .maxHedgedAttempts(1)
                .build()) {

            CompletableFuture<HedgedResult<String>> future = executor.submitAsync(attemptIndex -> {
                CompletableFuture<String> cf = new CompletableFuture<>();
                Thread.ofVirtual().start(() -> {
                    if (attemptIndex == 0) {
                        try {
                            Thread.sleep(5000);
                            cf.complete("slow-primary");
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    } else {
                        cf.complete("fast-hedge-" + attemptIndex);
                    }
                });
                cf.whenComplete((res, ex) -> {
                    if (attemptIndex == 0 && cf.isCancelled()) {
                        primaryInterrupted.set(true);
                        primaryCancelledLatch.countDown();
                    }
                });
                return cf;
            });

            HedgedResult<String> result = future.get(2, TimeUnit.SECONDS);

            assertEquals("fast-hedge-1", result.value());
            assertTrue(result.isHedgedWin());
            assertEquals(1, result.winningAttemptIndex());
            assertEquals(1, result.hedgesFiredForCall());
            assertEquals(1, result.cancelledAttemptsCount());
            assertTrue(primaryCancelledLatch.await(1, TimeUnit.SECONDS));
            assertTrue(primaryInterrupted.get());

            HedgedMetrics metrics = executor.metrics();
            assertEquals(0, metrics.primaryWins());
            assertEquals(1, metrics.hedgedWins());
            assertEquals(1, metrics.totalHedgesFired());
            assertEquals(1, metrics.cancelledRedundantCalls());
        }
    }

    @Test
    void nonRetriableExceptionFailsImmediatelyWithoutFiringHedges() {
        try (HedgedRequestExecutor executor = HedgedRequestExecutor.builder()
                .hedgeDelay(Duration.ofMillis(100))
                .maxHedgedAttempts(2)
                .hedgeOnException(ex -> !(ex instanceof IllegalArgumentException))
                .build()) {

            ExecutionException ex = assertThrows(ExecutionException.class, () ->
                    executor.executeWithMetadata(attemptIndex -> {
                        if (attemptIndex == 0) {
                            throw new IllegalArgumentException("Fatal bad request");
                        }
                        return "hedge-should-not-run";
                    })
            );

            assertInstanceOf(IllegalArgumentException.class, ex.getCause());
            assertEquals("Fatal bad request", ex.getCause().getMessage());

            HedgedMetrics metrics = executor.metrics();
            assertEquals(0, metrics.totalHedgesFired());
            assertEquals(0, metrics.primaryWins());
            assertEquals(0, metrics.hedgedWins());
        }
    }

    @Test
    void retriableExceptionTriggersImmediateHedgeWithoutWaitingForDelay() throws Exception {
        try (HedgedRequestExecutor executor = HedgedRequestExecutor.builder()
                .hedgeDelay(Duration.ofSeconds(10)) // Very long timer delay
                .maxHedgedAttempts(1)
                .hedgeOnException(ex -> ex instanceof TimeoutException || ex instanceof IOException)
                .build()) {

            long startMs = System.currentTimeMillis();
            HedgedResult<String> result = executor.executeWithMetadata(attemptIndex -> {
                if (attemptIndex == 0) {
                    throw new IOException("Transient network reset on primary");
                }
                return "immediate-hedge-recovery";
            });
            long elapsedMs = System.currentTimeMillis() - startMs;

            assertEquals("immediate-hedge-recovery", result.value());
            assertEquals(1, result.winningAttemptIndex());
            assertTrue(elapsedMs < 2000, "Immediate hedge should not wait for 10s hedgeDelay");
            assertEquals(1, executor.metrics().hedgedWins());
            assertEquals(1, executor.metrics().totalHedgesFired());
        }
    }

    @Test
    void allAttemptsFailPropagatesPrimaryExceptionWithSuppressedErrors() {
        try (HedgedRequestExecutor executor = HedgedRequestExecutor.builder()
                .hedgeDelay(Duration.ofMillis(20))
                .maxHedgedAttempts(2)
                .build()) {

            ExecutionException ex = assertThrows(ExecutionException.class, () ->
                    executor.execute(() -> {
                        throw new IOException("Backend unavailable");
                    })
            );

            assertInstanceOf(IOException.class, ex.getCause());
            assertEquals(2, ex.getCause().getSuppressed().length);
            assertEquals(2, executor.metrics().totalHedgesFired());
        }
    }
}
