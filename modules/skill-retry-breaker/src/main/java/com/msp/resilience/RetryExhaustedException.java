package com.msp.resilience;

import java.time.Duration;

/**
 * Exception thrown when all configured retry attempts have been exhausted.
 */
public class RetryExhaustedException extends RuntimeException {

    private final int totalAttempts;
    private final Duration totalDelay;

    public RetryExhaustedException(String message, Throwable cause, int totalAttempts, Duration totalDelay) {
        super(message, cause);
        this.totalAttempts = totalAttempts;
        this.totalDelay = totalDelay;
    }

    public int totalAttempts() {
        return totalAttempts;
    }

    public Duration totalDelay() {
        return totalDelay;
    }
}
