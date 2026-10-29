package com.msp.resilience;

import java.time.Duration;
import java.util.Optional;

/**
 * Result record holding outcome, attempts, and total backoff duration of a retried operation.
 */
public record RetryResult<T>(
        T value,
        Throwable exception,
        int attempts,
        Duration totalDelay,
        boolean isSuccess
) {
    public static <T> RetryResult<T> success(T value, int attempts, Duration totalDelay) {
        return new RetryResult<>(value, null, attempts, totalDelay, true);
    }

    public static <T> RetryResult<T> failure(Throwable exception, int attempts, Duration totalDelay) {
        return new RetryResult<>(null, exception, attempts, totalDelay, false);
    }

    public Optional<T> optionalValue() {
        return Optional.ofNullable(value);
    }
}
