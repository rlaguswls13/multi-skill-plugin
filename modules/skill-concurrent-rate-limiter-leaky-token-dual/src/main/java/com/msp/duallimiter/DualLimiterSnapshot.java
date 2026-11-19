package com.msp.duallimiter;

import java.time.Duration;

/**
 * Immutable snapshot of the dual rate limiter's internal state and metrics.
 *
 * @param availableTokens currently available tokens in token bucket
 * @param tokenCapacity   maximum burst capacity of token bucket
 * @param tokenRefillRate refill rate in tokens per second
 * @param queueSize       number of items currently queued in leaky bucket
 * @param queueCapacity   maximum leaky bucket queue capacity
 * @param leakRate        drain rate in items per second
 * @param acceptedCount   total number of requests admitted into system
 * @param rejectedCount   total number of requests rejected (burst or queue full)
 * @param drainedCount    total number of requests drained/executed from leaky bucket
 */
public record DualLimiterSnapshot(
        double availableTokens,
        double tokenCapacity,
        double tokenRefillRate,
        int queueSize,
        int queueCapacity,
        double leakRate,
        long acceptedCount,
        long rejectedCount,
        long drainedCount
) {
    /**
     * Estimates the delay for a newly queued request before it will be emitted.
     */
    public Duration estimatedQueueDelay() {
        if (leakRate <= 0.0 || queueSize == 0) {
            return Duration.ZERO;
        }
        long nanos = (long) ((queueSize / leakRate) * 1_000_000_000.0);
        return Duration.ofNanos(nanos);
    }
}
