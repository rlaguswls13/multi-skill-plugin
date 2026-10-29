package com.msp.stats;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Thread-safe batch and streaming percentile, IQR, and statistical metrics calculator.
 * <p>
 * Percentiles are computed using linear interpolation between closest ranks (R-7 / standard NIST).
 */
public final class PercentileCalculator {

    private final ReentrantLock lock = new ReentrantLock();
    private final List<Double> values = new ArrayList<>();
    private double[] sortedCache = null;
    private boolean dirty = false;

    private double sum = 0.0;
    private double min = Double.POSITIVE_INFINITY;
    private double max = Double.NEGATIVE_INFINITY;

    public PercentileCalculator() {
    }

    public PercentileCalculator(Collection<Double> initialValues) {
        if (initialValues != null && !initialValues.isEmpty()) {
            addAll(initialValues);
        }
    }

    /**
     * Adds a single value to the calculator.
     *
     * @param value numerical measurement
     * @return this calculator
     */
    public PercentileCalculator add(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException("Value must be a finite number: " + value);
        }
        lock.lock();
        try {
            values.add(value);
            sum += value;
            if (value < min) min = value;
            if (value > max) max = value;
            dirty = true;
            return this;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Adds a collection of values to the calculator.
     *
     * @param items collection of double values
     * @return this calculator
     */
    public PercentileCalculator addAll(Collection<Double> items) {
        if (items == null || items.isEmpty()) {
            return this;
        }
        lock.lock();
        try {
            for (Double v : items) {
                if (v == null || Double.isNaN(v) || Double.isInfinite(v)) {
                    throw new IllegalArgumentException("All values must be finite and non-null: " + v);
                }
                values.add(v);
                sum += v;
                if (v < min) min = v;
                if (v > max) max = v;
            }
            dirty = true;
            return this;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Adds varargs values.
     *
     * @param items array of values
     * @return this calculator
     */
    public PercentileCalculator addAll(double... items) {
        if (items == null || items.length == 0) {
            return this;
        }
        lock.lock();
        try {
            for (double v : items) {
                if (Double.isNaN(v) || Double.isInfinite(v)) {
                    throw new IllegalArgumentException("All values must be finite: " + v);
                }
                values.add(v);
                sum += v;
                if (v < min) min = v;
                if (v > max) max = v;
            }
            dirty = true;
            return this;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns the total count of collected values.
     */
    public int getCount() {
        lock.lock();
        try {
            return values.size();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Checks if calculator is empty.
     */
    public boolean isEmpty() {
        return getCount() == 0;
    }

    /**
     * Computes the requested percentile in [0.0, 100.0] using linear interpolation.
     *
     * @param percentile value between 0.0 and 100.0
     * @return calculated percentile value
     * @throws NoSuchElementException if calculator is empty
     * @throws IllegalArgumentException if percentile is outside [0.0, 100.0]
     */
    public double getPercentile(double percentile) {
        if (percentile < 0.0 || percentile > 100.0 || Double.isNaN(percentile)) {
            throw new IllegalArgumentException("Percentile must be between 0.0 and 100.0, got: " + percentile);
        }
        lock.lock();
        try {
            ensureSorted();
            int n = sortedCache.length;
            if (n == 0) {
                throw new NoSuchElementException("Cannot calculate percentile from empty dataset");
            }
            if (n == 1) {
                return sortedCache[0];
            }
            if (percentile == 0.0) {
                return sortedCache[0];
            }
            if (percentile == 100.0) {
                return sortedCache[n - 1];
            }

            double rank = (n - 1) * (percentile / 100.0);
            int lowerIndex = (int) Math.floor(rank);
            int upperIndex = lowerIndex + 1;
            double fraction = rank - lowerIndex;

            if (upperIndex >= n) {
                return sortedCache[n - 1];
            }
            return sortedCache[lowerIndex] + fraction * (sortedCache[upperIndex] - sortedCache[lowerIndex]);
        } finally {
            lock.unlock();
        }
    }

    /** 50th percentile (Median) */
    public double getP50() {
        return getPercentile(50.0);
    }

    /** 90th percentile */
    public double getP90() {
        return getPercentile(90.0);
    }

    /** 95th percentile */
    public double getP95() {
        return getPercentile(95.0);
    }

    /** 99th percentile */
    public double getP99() {
        return getPercentile(99.0);
    }

    /** Alias for P50 */
    public double getMedian() {
        return getP50();
    }

    /**
     * Interquartile Range (IQR = Q3 - Q1 = P75 - P25).
     */
    public double getIQR() {
        return getPercentile(75.0) - getPercentile(25.0);
    }

    /** Minimum value */
    public double getMin() {
        lock.lock();
        try {
            if (values.isEmpty()) {
                throw new NoSuchElementException("Dataset is empty");
            }
            return min;
        } finally {
            lock.unlock();
        }
    }

    /** Maximum value */
    public double getMax() {
        lock.lock();
        try {
            if (values.isEmpty()) {
                throw new NoSuchElementException("Dataset is empty");
            }
            return max;
        } finally {
            lock.unlock();
        }
    }

    /** Arithmetic mean */
    public double getMean() {
        lock.lock();
        try {
            if (values.isEmpty()) {
                throw new NoSuchElementException("Dataset is empty");
            }
            return sum / values.size();
        } finally {
            lock.unlock();
        }
    }

    /** Sample variance (Bessel's correction, N - 1) */
    public double getVariance() {
        lock.lock();
        try {
            int n = values.size();
            if (n == 0) {
                throw new NoSuchElementException("Dataset is empty");
            }
            if (n == 1) {
                return 0.0;
            }
            double mean = sum / n;
            double sumOfSquares = 0.0;
            for (double v : values) {
                double diff = v - mean;
                sumOfSquares += diff * diff;
            }
            return sumOfSquares / (n - 1);
        } finally {
            lock.unlock();
        }
    }

    /** Sample standard deviation */
    public double getStandardDeviation() {
        return Math.sqrt(getVariance());
    }

    /** Resets the calculator */
    public void reset() {
        lock.lock();
        try {
            values.clear();
            sortedCache = null;
            dirty = false;
            sum = 0.0;
            min = Double.POSITIVE_INFINITY;
            max = Double.NEGATIVE_INFINITY;
        } finally {
            lock.unlock();
        }
    }

    /** Returns an immutable snapshot copy of all collected values */
    public List<Double> getSnapshot() {
        lock.lock();
        try {
            return Collections.unmodifiableList(new ArrayList<>(values));
        } finally {
            lock.unlock();
        }
    }

    private void ensureSorted() {
        if (dirty || sortedCache == null) {
            sortedCache = new double[values.size()];
            for (int i = 0; i < values.size(); i++) {
                sortedCache[i] = values.get(i);
            }
            Arrays.sort(sortedCache);
            dirty = false;
        }
    }
}
