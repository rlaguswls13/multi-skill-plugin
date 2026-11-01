package com.msp.jittervis;

/**
 * Represents a single interval bin within a jitter distribution histogram.
 */
public record HistogramBin(
        long rangeStartMs,
        long rangeEndMs,
        int count,
        double percentage
) {
}
