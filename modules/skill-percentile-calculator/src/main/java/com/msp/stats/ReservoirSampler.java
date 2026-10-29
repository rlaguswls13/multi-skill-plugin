package com.msp.stats;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Thread-safe streaming reservoir sampler (Vitter's Algorithm R) for maintaining
 * an unbiased random sample of fixed size k from an unbounded or infinite stream.
 */
public final class ReservoirSampler {

    private final int capacity;
    private final Random random;
    private final double[] reservoir;
    private final ReentrantLock lock = new ReentrantLock();
    private long totalSeen = 0;

    public ReservoirSampler(int capacity) {
        this(capacity, new Random());
    }

    public ReservoirSampler(int capacity, Random random) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be positive, got: " + capacity);
        }
        if (random == null) {
            throw new NullPointerException("Random generator cannot be null");
        }
        this.capacity = capacity;
        this.random = random;
        this.reservoir = new double[capacity];
    }

    /**
     * Considers a single incoming stream item for inclusion in the reservoir.
     */
    public ReservoirSampler add(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException("Value must be a finite number: " + value);
        }
        lock.lock();
        try {
            totalSeen++;
            if (totalSeen <= capacity) {
                reservoir[(int) (totalSeen - 1)] = value;
            } else {
                // With probability capacity / totalSeen, replace a random element
                long replaceIdx = nextLong(totalSeen);
                if (replaceIdx < capacity) {
                    reservoir[(int) replaceIdx] = value;
                }
            }
            return this;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Ingests a collection of values.
     */
    public ReservoirSampler addAll(Collection<Double> values) {
        if (values == null || values.isEmpty()) {
            return this;
        }
        lock.lock();
        try {
            for (Double v : values) {
                if (v == null) continue;
                add(v);
            }
            return this;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Ingests varargs values.
     */
    public ReservoirSampler addAll(double... values) {
        if (values == null || values.length == 0) {
            return this;
        }
        lock.lock();
        try {
            for (double v : values) {
                add(v);
            }
            return this;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Maximum capacity of the reservoir.
     */
    public int getCapacity() {
        return capacity;
    }

    /**
     * Total number of items observed so far across the entire stream.
     */
    public long getTotalSeen() {
        lock.lock();
        try {
            return totalSeen;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Current number of sampled items stored (min(totalSeen, capacity)).
     */
    public int getSampleSize() {
        lock.lock();
        try {
            return (int) Math.min(totalSeen, capacity);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns an immutable snapshot list of the current reservoir samples.
     */
    public List<Double> getSamples() {
        lock.lock();
        try {
            int currentSize = getSampleSize();
            List<Double> list = new ArrayList<>(currentSize);
            for (int i = 0; i < currentSize; i++) {
                list.add(reservoir[i]);
            }
            return Collections.unmodifiableList(list);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns a copy of the current sample array.
     */
    public double[] sampleArray() {
        lock.lock();
        try {
            int currentSize = getSampleSize();
            return Arrays.copyOf(reservoir, currentSize);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Feeds the current reservoir samples into a new PercentileCalculator instance.
     */
    public PercentileCalculator toPercentileCalculator() {
        lock.lock();
        try {
            PercentileCalculator calculator = new PercentileCalculator();
            int currentSize = getSampleSize();
            for (int i = 0; i < currentSize; i++) {
                calculator.add(reservoir[i]);
            }
            return calculator;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Resets the sampler.
     */
    public void reset() {
        lock.lock();
        try {
            totalSeen = 0;
            Arrays.fill(reservoir, 0.0);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Generates a uniformly distributed long in [0, bound).
     */
    private long nextLong(long bound) {
        if (bound <= Integer.MAX_VALUE) {
            return random.nextInt((int) bound);
        }
        long bits, val;
        do {
            bits = (random.nextLong() << 1) >>> 1;
            val = bits % bound;
        } while (bits - val + (bound - 1) < 0L);
        return val;
    }
}
