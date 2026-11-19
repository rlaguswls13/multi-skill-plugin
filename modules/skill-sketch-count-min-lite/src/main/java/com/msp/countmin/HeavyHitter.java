package com.msp.countmin;

/**
 * Represents a heavy hitter item detected in a data stream.
 *
 * @param item               the item
 * @param estimatedFrequency estimated occurrence frequency
 * @param frequencyRatio     ratio of item frequency relative to total stream count
 */
public record HeavyHitter<T>(T item, long estimatedFrequency, double frequencyRatio)
        implements Comparable<HeavyHitter<T>> {

    @Override
    public int compareTo(HeavyHitter<T> o) {
        // Descending order of estimated frequency
        return Long.compare(o.estimatedFrequency, this.estimatedFrequency);
    }
}
