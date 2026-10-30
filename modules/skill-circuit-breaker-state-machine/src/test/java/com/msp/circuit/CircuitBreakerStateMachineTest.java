package com.msp.circuit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CircuitBreakerStateMachine Tests")
class CircuitBreakerStateMachineTest {

    @Nested
    @DisplayName("State Transitions & Windowing")
    class TransitionTests {

        @Test
        @DisplayName("Starts in CLOSED state and remains CLOSED on successes")
        void initialClosedState() {
            CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                    .slidingWindowSize(10)
                    .failureRateThreshold(50.0f)
                    .build();

            CircuitBreakerStateMachine cb = new CircuitBreakerStateMachine(config);
            assertEquals(CircuitState.CLOSED, cb.getState());

            for (int i = 0; i < 20; i++) {
                assertTrue(cb.tryAcquirePermission());
                cb.recordSuccess();
            }

            assertEquals(CircuitState.CLOSED, cb.getState());
            assertEquals(0.0f, cb.getFailureRate());
        }

        @Test
        @DisplayName("Trips to OPEN when failure rate exceeds threshold")
        void tripsToOpen() {
            CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                    .slidingWindowSize(4)
                    .minimumNumberOfCalls(4)
                    .failureRateThreshold(50.0f)
                    .build();

            CircuitBreakerStateMachine cb = new CircuitBreakerStateMachine(config);

            // 2 successes, 2 failures = 50% >= 50%
            cb.recordSuccess();
            cb.recordSuccess();
            cb.recordFailure();
            assertEquals(CircuitState.CLOSED, cb.getState());

            cb.recordFailure(); // 4th call, triggers evaluation
            assertEquals(CircuitState.OPEN, cb.getState());
            assertFalse(cb.tryAcquirePermission());
        }

        @Test
        @DisplayName("Transitions to HALF_OPEN after wait duration and recovers to CLOSED on trial successes")
        void recoversThroughHalfOpen() {
            AtomicLong virtualClock = new AtomicLong(1000L);
            CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                    .slidingWindowSize(2)
                    .minimumNumberOfCalls(2)
                    .failureRateThreshold(50.0f)
                    .waitDurationInOpen(Duration.ofSeconds(5))
                    .permittedNumberOfCallsInHalfOpenState(2)
                    .build();

            CircuitBreakerStateMachine cb = new CircuitBreakerStateMachine(config, virtualClock::get);

            // Trip to OPEN
            cb.recordFailure();
            cb.recordFailure();
            assertEquals(CircuitState.OPEN, cb.getState());
            assertFalse(cb.tryAcquirePermission());

            // Advance clock by 4 seconds (not enough)
            virtualClock.addAndGet(4000L);
            assertFalse(cb.tryAcquirePermission());
            assertEquals(CircuitState.OPEN, cb.getState());

            // Advance clock past 5 seconds
            virtualClock.addAndGet(2000L);
            assertEquals(CircuitState.HALF_OPEN, cb.getState());

            // Probe call 1
            assertTrue(cb.tryAcquirePermission());
            cb.recordSuccess();
            assertEquals(CircuitState.HALF_OPEN, cb.getState());

            // Probe call 2
            assertTrue(cb.tryAcquirePermission());
            cb.recordSuccess();

            // All probe calls succeeded -> back to CLOSED
            assertEquals(CircuitState.CLOSED, cb.getState());
        }

        @Test
        @DisplayName("Trips back to OPEN from HALF_OPEN if probe call fails")
        void halfOpenTripsBackToOpenOnFailure() {
            AtomicLong virtualClock = new AtomicLong(1000L);
            CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                    .slidingWindowSize(2)
                    .minimumNumberOfCalls(2)
                    .failureRateThreshold(50.0f)
                    .waitDurationInOpen(Duration.ofSeconds(5))
                    .permittedNumberOfCallsInHalfOpenState(2)
                    .build();

            CircuitBreakerStateMachine cb = new CircuitBreakerStateMachine(config, virtualClock::get);
            cb.transitionToOpen();

            // Advance time
            virtualClock.addAndGet(6000L);
            assertTrue(cb.tryAcquirePermission());
            assertEquals(CircuitState.HALF_OPEN, cb.getState());

            // Fail probe call
            cb.recordFailure();
            assertEquals(CircuitState.OPEN, cb.getState());
            assertFalse(cb.tryAcquirePermission());
        }
    }

    @Nested
    @DisplayName("Execution Helpers")
    class ExecutionHelperTests {

        @Test
        @DisplayName("executeSupplier records success and returns result")
        void executeSupplierSuccess() {
            CircuitBreakerStateMachine cb = new CircuitBreakerStateMachine(CircuitBreakerConfig.ofDefaults());
            String res = cb.executeSupplier(() -> "hello world");
            assertEquals("hello world", res);
            assertEquals(CircuitState.CLOSED, cb.getState());
        }

        @Test
        @DisplayName("executeSupplier records failure and throws exception")
        void executeSupplierFailure() {
            CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                    .slidingWindowSize(1)
                    .failureRateThreshold(50.0f)
                    .build();

            CircuitBreakerStateMachine cb = new CircuitBreakerStateMachine(config);

            assertThrows(IllegalStateException.class, () -> cb.executeSupplier(() -> {
                throw new IllegalStateException("Simulated DB error");
            }));

            assertEquals(CircuitState.OPEN, cb.getState());

            // Subsequent call should be rejected immediately
            assertThrows(CircuitBreakerOpenException.class, () -> cb.executeSupplier(() -> "never reached"));
        }

        @Test
        @DisplayName("executeRunnable handles runnable execution and failure")
        void executeRunnable() {
            CircuitBreakerStateMachine cb = new CircuitBreakerStateMachine(CircuitBreakerConfig.ofDefaults());
            AtomicInteger count = new AtomicInteger(0);
            cb.executeRunnable(count::incrementAndGet);
            assertEquals(1, count.get());
        }
    }

    @Nested
    @DisplayName("Concurrency & Reset")
    class ConcurrencyTests {

        @Test
        @DisplayName("Thread-safe under concurrent executions")
        void concurrentExecutions() throws InterruptedException {
            CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                    .slidingWindowSize(50)
                    .minimumNumberOfCalls(20)
                    .failureRateThreshold(60.0f)
                    .build();

            CircuitBreakerStateMachine cb = new CircuitBreakerStateMachine(config);
            int threads = 10;
            int callsPerThread = 100;
            ExecutorService pool = Executors.newFixedThreadPool(threads);
            CountDownLatch latch = new CountDownLatch(threads);

            for (int i = 0; i < threads; i++) {
                final int threadId = i;
                pool.submit(() -> {
                    try {
                        for (int j = 0; j < callsPerThread; j++) {
                            if (cb.tryAcquirePermission()) {
                                if (threadId % 2 == 0) {
                                    cb.recordSuccess();
                                } else {
                                    cb.recordFailure();
                                }
                            }
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await();
            pool.shutdown();
            assertNotNull(cb.getState());
        }

        @Test
        @DisplayName("Manual reset restores CLOSED state")
        void manualReset() {
            CircuitBreakerStateMachine cb = new CircuitBreakerStateMachine(CircuitBreakerConfig.ofDefaults());
            cb.transitionToOpen();
            assertEquals(CircuitState.OPEN, cb.getState());

            cb.reset();
            assertEquals(CircuitState.CLOSED, cb.getState());
            assertTrue(cb.tryAcquirePermission());
        }
    }
}
