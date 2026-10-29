package com.msp.stats;

/**
 * Represents a single interval in a histogram.
 *
 * @param lowerBound inclusive lower bound
 * @param upperBound upper bound (exclusive, except for the last bucket which is inclusive)
 * @param count      number of samples falling into this bucket
 * @param percentage ratio of samples in this bucket relative to total count (0.0 to 100.0)
 */
public record Bucket(
    double lowerBound,
    double upperBound,
    long count,
    double percentage
) {
    public Bucket {
        if (lowerBound > upperBound) {
            throw new IllegalArgumentException("lowerBound cannot exceed upperBound: " + lowerBound + " > " + upperBound);
        }
        if (count < 0) {
            throw new IllegalArgumentException("count cannot be negative: " + count);
        }
    }

    /**
     * Checks if the given value falls within this bucket interval.
     *
     * @param value        the value to test
     * @param isLastBucket true if this is the final bucket (makes upperBound inclusive)
     */
    public boolean contains(double value, boolean isLastBucket) {
        if (isLastBucket) {
            return value >= lowerBound && value <= upperBound;
        }
        return value >= lowerBound && value < upperBound;
    }
}
