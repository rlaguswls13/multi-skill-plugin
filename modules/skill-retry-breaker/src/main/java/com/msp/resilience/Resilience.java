package com.msp.resilience;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Unified facade for resilience utilities (retries and circuit breakers).
 */
public final class Resilience {

    private Resilience() {
    }

    /**
     * Creates a RetryExecutor with the given policy.
     */
    public static RetryExecutor retry(RetryPolicy policy) {
        return new RetryExecutor(policy);
    }

    /**
     * Creates a simple RetryExecutor with max attempts and fixed initial delay.
     */
    public static RetryExecutor retry(int maxAttempts, Duration initialDelay) {
        return new RetryExecutor(
                RetryPolicy.builder()
                        .maxAttempts(maxAttempts)
                        .initialDelay(initialDelay)
                        .multiplier(1.0)
                        .build()
        );
    }

    /**
     * Creates a CircuitBreaker builder with the given name.
     */
    public static CircuitBreaker.Builder circuitBreaker(String name) {
        return CircuitBreaker.builder(name);
    }

    /**
     * Combines CircuitBreaker and RetryExecutor to run an operation.
     * The circuit breaker wraps the retry executor so that calls rejected by circuit breaker do not retry pointlessly.
     */
    public static <T> T executeWith(CircuitBreaker circuitBreaker, RetryExecutor retryExecutor, Supplier<T> action) {
        Objects.requireNonNull(circuitBreaker, "circuitBreaker cannot be null");
        Objects.requireNonNull(retryExecutor, "retryExecutor cannot be null");
        Objects.requireNonNull(action, "action cannot be null");

        return circuitBreaker.execute(() -> retryExecutor.execute(action));
    }
}
