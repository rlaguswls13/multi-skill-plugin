package com.msp.sseclient;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Exponential backoff policy with randomized jitter for SSE reconnections.
 */
public record BackoffPolicy(
        Duration initialDelay,
        Duration maxDelay,
        double multiplier,
        double jitterFactor
) {
    public static final BackoffPolicy DEFAULT = new BackoffPolicy(
            Duration.ofMillis(1000),
            Duration.ofMillis(30000),
            2.0,
            0.2
    );

    public BackoffPolicy {
        Objects.requireNonNull(initialDelay, "initialDelay must not be null");
        Objects.requireNonNull(maxDelay, "maxDelay must not be null");
        if (multiplier < 1.0) {
            throw new IllegalArgumentException("multiplier must be >= 1.0");
        }
        if (jitterFactor < 0.0 || jitterFactor > 1.0) {
            throw new IllegalArgumentException("jitterFactor must be between 0.0 and 1.0");
        }
    }

    /**
     * Calculates the backoff delay for a given attempt index (0-based).
     *
     * @param attempt               The attempt index (0, 1, 2, ...)
     * @param retryOverrideMillis   Optional override delay in milliseconds from an SSE 'retry:' field
     * @return Calculated backoff duration with jitter applied
     */
    public Duration calculateDelay(int attempt, Long retryOverrideMillis) {
        long baseMillis;
        if (retryOverrideMillis != null && retryOverrideMillis > 0) {
            baseMillis = retryOverrideMillis;
        } else {
            baseMillis = initialDelay.toMillis();
        }

        // Apply exponential growth based on attempt
        double expFactor = Math.pow(multiplier, Math.min(attempt, 30));
        double calculatedMillis = baseMillis * expFactor;
        long cappedMillis = Math.min((long) calculatedMillis, maxDelay.toMillis());

        // Apply jitter: [cappedMillis * (1 - jitterFactor), cappedMillis * (1 + jitterFactor)]
        if (jitterFactor > 0.0 && cappedMillis > 0) {
            double min = cappedMillis * (1.0 - jitterFactor);
            double max = cappedMillis * (1.0 + jitterFactor);
            long jittered = (long) ThreadLocalRandom.current().nextDouble(min, max + 1.0);
            return Duration.ofMillis(Math.max(0, jittered));
        }

        return Duration.ofMillis(Math.max(0, cappedMillis));
    }
}
