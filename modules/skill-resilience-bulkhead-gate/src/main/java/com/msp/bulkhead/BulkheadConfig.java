package com.msp.bulkhead;

import java.time.Duration;
import java.util.Objects;

/**
 * Configuration options for {@link BulkheadGate}.
 *
 * @param maxConcurrentCalls Maximum number of concurrent executions permitted.
 * @param maxWaitQueueSize   Maximum number of caller threads allowed to wait when permits are exhausted.
 * @param maxWaitDuration    Maximum time callers will wait for an available permit.
 */
public record BulkheadConfig(
        int maxConcurrentCalls,
        int maxWaitQueueSize,
        Duration maxWaitDuration
) {
    public BulkheadConfig {
        if (maxConcurrentCalls <= 0) {
            throw new IllegalArgumentException("maxConcurrentCalls must be positive: " + maxConcurrentCalls);
        }
        if (maxWaitQueueSize < 0) {
            throw new IllegalArgumentException("maxWaitQueueSize must not be negative: " + maxWaitQueueSize);
        }
        Objects.requireNonNull(maxWaitDuration, "maxWaitDuration must not be null");
        if (maxWaitDuration.isNegative()) {
            throw new IllegalArgumentException("maxWaitDuration must not be negative");
        }
    }

    /**
     * Creates a config with the given max concurrent calls, 0 wait queue, and zero wait duration.
     */
    public static BulkheadConfig of(int maxConcurrentCalls) {
        return new BulkheadConfig(maxConcurrentCalls, 0, Duration.ZERO);
    }

    /**
     * Creates a config with custom concurrency and wait queue parameters.
     */
    public static BulkheadConfig of(int maxConcurrentCalls, int maxWaitQueueSize, Duration maxWaitDuration) {
        return new BulkheadConfig(maxConcurrentCalls, maxWaitQueueSize, maxWaitDuration);
    }

    /**
     * Creates default configuration with 10 max concurrent calls, 10 wait queue, and 500ms max wait duration.
     */
    public static BulkheadConfig ofDefaults() {
        return new BulkheadConfig(10, 10, Duration.ofMillis(500));
    }
}
