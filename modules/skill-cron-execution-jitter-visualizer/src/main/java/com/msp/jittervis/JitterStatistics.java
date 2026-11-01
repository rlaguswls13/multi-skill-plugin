package com.msp.jittervis;

/**
 * Statistical metrics quantifying the dispersion and concurrency smoothing of jitter.
 */
public record JitterStatistics(
        long minDelayMs,
        long maxDelayMs,
        double meanDelayMs,
        double medianDelayMs,
        double variance,
        double stdDevMs,
        double coefficientOfVariation,
        int peakConcurrencyWithoutJitter,
        int peakConcurrencyWithJitter,
        double collisionReductionPercent
) {
}
