package com.msp.webhook;

import java.time.Duration;
import java.util.Objects;

/**
 * Configuration for exponential backoff retries when webhook delivery fails.
 */
public record RetryPolicy(
        int maxAttempts,
        Duration initialDelay,
        double backoffMultiplier,
        Duration maxDelay
) {

    public RetryPolicy {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be >= 1");
        }
        if (backoffMultiplier < 1.0) {
            throw new IllegalArgumentException("backoffMultiplier must be >= 1.0");
        }
        initialDelay = initialDelay != null ? initialDelay : Duration.ofMillis(100);
        maxDelay = maxDelay != null ? maxDelay : Duration.ofSeconds(5);
    }

    public static RetryPolicy defaultPolicy() {
        return new RetryPolicy(3, Duration.ofMillis(100), 2.0, Duration.ofSeconds(5));
    }

    public static RetryPolicy noRetry() {
        return new RetryPolicy(1, Duration.ZERO, 1.0, Duration.ZERO);
    }

    public static RetryPolicy of(int maxAttempts, Duration initialDelay, double multiplier, Duration maxDelay) {
        return new RetryPolicy(maxAttempts, initialDelay, multiplier, maxDelay);
    }

    /**
     * Calculates the backoff delay duration for a specific retry attempt (1-based index).
     */
    public Duration delayForAttempt(int attempt) {
        if (attempt <= 1 || initialDelay.isZero()) {
            return Duration.ZERO;
        }

        double factor = Math.pow(backoffMultiplier, attempt - 1);
        long delayMillis = (long) (initialDelay.toMillis() * factor);

        if (delayMillis > maxDelay.toMillis()) {
            return maxDelay;
        }
        return Duration.ofMillis(delayMillis);
    }
}
