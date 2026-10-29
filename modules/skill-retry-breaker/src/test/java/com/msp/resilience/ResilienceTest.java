package com.msp.resilience;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Resilience (Retry & CircuitBreaker) Tests")
class ResilienceTest {

    @Nested
    @DisplayName("RetryPolicy & RetryExecutor Tests")
    class RetryTests {

        @Test
        @DisplayName("Should succeed on first attempt without retrying")
        void immediateSuccess() {
            RetryPolicy policy = RetryPolicy.defaults();
            RetryExecutor executor = new RetryExecutor(policy, d -> {});

            AtomicInteger calls = new AtomicInteger(0);
            String result = executor.execute(() -> {
                calls.incrementAndGet();
                return "hello";
            });

            assertEquals("hello", result);
            assertEquals(1, calls.get());
        }

        @Test
        @DisplayName("Should retry on failures and succeed eventually")
        void retryUntilSuccess() {
            RetryPolicy policy = RetryPolicy.builder()
                    .maxAttempts(4)
                    .initialDelay(Duration.ofMillis(10))
                    .multiplier(2.0)
                    .build();

            AtomicInteger calls = new AtomicInteger(0);
            RetryExecutor executor = new RetryExecutor(policy, d -> {});

            String result = executor.execute(() -> {
                int attempt = calls.incrementAndGet();
                if (attempt < 3) {
                    throw new IllegalStateException("Transient error on attempt " + attempt);
                }
                return "success-on-3";
            });

            assertEquals("success-on-3", result);
            assertEquals(3, calls.get());
        }

        @Test
        @DisplayName("Should exhaust attempts and throw RetryExhaustedException")
        void retryExhaustion() {
            RetryPolicy policy = RetryPolicy.builder()
                    .maxAttempts(3)
                    .initialDelay(Duration.ofMillis(5))
                    .build();

            AtomicInteger calls = new AtomicInteger(0);
            RetryExecutor executor = new RetryExecutor(policy, d -> {});

            RetryExhaustedException ex = assertThrows(RetryExhaustedException.class, () ->
                    executor.execute(() -> {
                        calls.incrementAndGet();
                        throw new RuntimeException("Permanent failure");
                    })
            );

            assertEquals(3, ex.totalAttempts());
            assertEquals(3, calls.get());
            assertInstanceOf(RuntimeException.class, ex.getCause());
        }

        @Test
        @DisplayName("Should not retry when exception matches abortOn predicate")
        void abortOnPredicate() {
            RetryPolicy policy = RetryPolicy.builder()
                    .maxAttempts(5)
                    .abortOn(IllegalArgumentException.class)
                    .build();

            AtomicInteger calls = new AtomicInteger(0);
            RetryExecutor executor = new RetryExecutor(policy, d -> {});

            assertThrows(RetryExhaustedException.class, () ->
                    executor.execute(() -> {
                        calls.incrementAndGet();
                        throw new IllegalArgumentException("Do not retry this");
                    })
            );

            assertEquals(1, calls.get(), "Should abort immediately on first attempt");
        }

        @Test
        @DisplayName("Backoff delays with multiplier and capping")
        void backoffDelays() {
            RetryPolicy policy = RetryPolicy.builder()
                    .maxAttempts(5)
                    .initialDelay(Duration.ofMillis(100))
                    .multiplier(2.0)
                    .maxDelay(Duration.ofMillis(300))
                    .jitter(Jitter.NONE)
                    .build();

            assertEquals(Duration.ofMillis(100), policy.computeDelay(1));
            assertEquals(Duration.ofMillis(200), policy.computeDelay(2));
            // attempt 3: 100 * 4 = 400ms -> capped to 300ms
            assertEquals(Duration.ofMillis(300), policy.computeDelay(3));
            assertEquals(Duration.ofMillis(300), policy.computeDelay(4));
        }

        @Test
        @DisplayName("Jitter strategies calculate non-negative bounded delays")
        void jitterStrategies() {
            RetryPolicy fullJitter = RetryPolicy.builder()
                    .initialDelay(Duration.ofMillis(100))
                    .maxDelay(Duration.ofMillis(500))
                    .jitter(Jitter.FULL)
                    .build();

            for (int i = 0; i < 20; i++) {
                Duration d = fullJitter.computeDelay(3);
                assertTrue(d.toMillis() >= 0 && d.toMillis() <= 400);
            }

            RetryPolicy equalJitter = RetryPolicy.builder()
                    .initialDelay(Duration.ofMillis(100))
                    .maxDelay(Duration.ofMillis(500))
                    .jitter(Jitter.EQUAL)
                    .build();

            for (int i = 0; i < 20; i++) {
                Duration d = equalJitter.computeDelay(2); // base 200ms
                assertTrue(d.toMillis() >= 100 && d.toMillis() <= 200);
            }
        }
    }

    @Nested
    @DisplayName("CircuitBreaker Tests")
    class CircuitBreakerTests {

        @Test
        @DisplayName("Initial state should be CLOSED")
        void initialStateClosed() {
            CircuitBreaker cb = Resilience.circuitBreaker("test-service")
                    .failureRateThreshold(50.0)
                    .minimumCalls(5)
                    .build();

            assertEquals(CircuitBreaker.State.CLOSED, cb.getState());
            assertTrue(cb.tryAcquirePermission());
        }

        @Test
        @DisplayName("Should trip to OPEN when failure rate exceeds threshold after minimum calls")
        void tripToOpenOnFailures() {
            CircuitBreaker cb = Resilience.circuitBreaker("order-api")
                    .failureRateThreshold(50.0)
                    .minimumCalls(4)
                    .slidingWindowSize(4)
                    .waitDurationInOpen(Duration.ofMillis(200))
                    .build();

            // 1 success, 3 failures -> 75% failure rate (>= 50%)
            cb.recordSuccess();
            cb.recordFailure(new IOException("Err 1"));
            cb.recordFailure(new IOException("Err 2"));
            assertEquals(CircuitBreaker.State.CLOSED, cb.getState()); // only 3 calls so far

            cb.recordFailure(new IOException("Err 3")); // 4th call
            assertEquals(CircuitBreaker.State.OPEN, cb.getState());

            // Calls should now be rejected immediately
            assertFalse(cb.tryAcquirePermission());
            assertThrows(CircuitBreakerOpenException.class, () -> cb.execute(() -> "test"));
        }

        @Test
        @DisplayName("Should transition from OPEN to HALF_OPEN after wait duration and recover to CLOSED")
        void recoverViaHalfOpen() throws InterruptedException {
            CircuitBreaker cb = Resilience.circuitBreaker("payment-gw")
                    .failureRateThreshold(50.0)
                    .minimumCalls(2)
                    .slidingWindowSize(2)
                    .waitDurationInOpen(Duration.ofMillis(100))
                    .permittedCallsInHalfOpen(2)
                    .build();

            // Trip to OPEN
            cb.recordFailure(new RuntimeException());
            cb.recordFailure(new RuntimeException());
            assertEquals(CircuitBreaker.State.OPEN, cb.getState());

            // Wait until wait duration passes
            Thread.sleep(150);

            assertEquals(CircuitBreaker.State.HALF_OPEN, cb.getState());
            assertTrue(cb.tryAcquirePermission());

            // In HALF_OPEN: 2 consecutive successes should recover to CLOSED
            cb.recordSuccess();
            assertEquals(CircuitBreaker.State.HALF_OPEN, cb.getState());

            cb.recordSuccess();
            assertEquals(CircuitBreaker.State.CLOSED, cb.getState());
        }

        @Test
        @DisplayName("Failure in HALF_OPEN should immediately trip back to OPEN")
        void failureInHalfOpenTripsBackToOpen() throws InterruptedException {
            CircuitBreaker cb = Resilience.circuitBreaker("inventory-service")
                    .failureRateThreshold(50.0)
                    .minimumCalls(2)
                    .waitDurationInOpen(Duration.ofMillis(80))
                    .permittedCallsInHalfOpen(2)
                    .build();

            // Trip to OPEN
            cb.recordFailure(new RuntimeException());
            cb.recordFailure(new RuntimeException());
            assertEquals(CircuitBreaker.State.OPEN, cb.getState());

            Thread.sleep(120);
            assertEquals(CircuitBreaker.State.HALF_OPEN, cb.getState());

            // A failure during HALF_OPEN trial
            cb.recordFailure(new RuntimeException("Still failing"));
            assertEquals(CircuitBreaker.State.OPEN, cb.getState());
        }

        @Test
        @DisplayName("Concurrent execution should maintain consistent state")
        void concurrentExecution() throws InterruptedException {
            CircuitBreaker cb = Resilience.circuitBreaker("concurrent-cb")
                    .failureRateThreshold(60.0)
                    .minimumCalls(10)
                    .slidingWindowSize(20)
                    .build();

            int threadCount = 20;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(1);

            for (int i = 0; i < threadCount; i++) {
                final int idx = i;
                executor.submit(() -> {
                    try {
                        latch.await();
                        if (idx % 2 == 0) {
                            cb.recordSuccess();
                        } else {
                            cb.recordFailure(new RuntimeException());
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }

            latch.countDown();
            executor.shutdown();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

            CircuitBreaker.Stats stats = cb.getStats();
            assertEquals(20, stats.totalCalls());
            assertEquals(10, stats.failedCalls());
            assertEquals(50.0, stats.failureRate(), 0.01);
            assertEquals(CircuitBreaker.State.CLOSED, stats.state());
        }
    }

    @Nested
    @DisplayName("Combined Resilience Tests")
    class CombinedTests {

        @Test
        @DisplayName("executeWith should coordinate circuit breaker and retry executor")
        void combinedExecution() {
            CircuitBreaker cb = Resilience.circuitBreaker("combined-test")
                    .failureRateThreshold(50.0)
                    .minimumCalls(5)
                    .build();

            RetryPolicy policy = RetryPolicy.builder()
                    .maxAttempts(3)
                    .initialDelay(Duration.ofMillis(1))
                    .build();

            RetryExecutor retry = new RetryExecutor(policy, d -> {});

            AtomicInteger counter = new AtomicInteger(0);
            String result = Resilience.executeWith(cb, retry, () -> {
                if (counter.incrementAndGet() < 2) {
                    throw new RuntimeException("Transient failure");
                }
                return "combined-success";
            });

            assertEquals("combined-success", result);
            assertEquals(2, counter.get());
            assertEquals(CircuitBreaker.State.CLOSED, cb.getState());
        }
    }
}
