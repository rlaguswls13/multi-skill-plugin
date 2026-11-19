package com.msp.tdigest;

/**
 * Immutable snapshot of a single T-Digest centroid (weighted cluster mean).
 *
 * @param mean   weighted arithmetic mean of samples in this centroid
 * @param weight positive total weight of samples in this centroid
 */
public record Centroid(double mean, double weight) implements Comparable<Centroid> {

    public Centroid {
        if (!Double.isFinite(mean)) {
            throw new IllegalArgumentException("Centroid mean must be finite: " + mean);
        }
        if (!Double.isFinite(weight) || weight <= 0.0) {
            throw new IllegalArgumentException("Centroid weight must be finite and > 0: " + weight);
        }
    }

    @Override
    public int compareTo(Centroid other) {
        return Double.compare(this.mean, other.mean);
    }
}
