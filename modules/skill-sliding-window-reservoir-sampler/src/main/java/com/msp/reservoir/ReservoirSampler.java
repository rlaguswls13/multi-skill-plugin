package com.msp.reservoir;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * Thread-safe implementation of Reservoir Sampling (Algorithm R) by Alan G. Waterman.
 * Provides uniform random representative sampling of size {@code k} from an unbounded or streaming dataset.
 *
 * @param <T> the type of elements being sampled
 */
public final class ReservoirSampler<T> {

    private final int capacity;
    private final Random random;
    private final List<T> reservoir;
    private long totalSeen;

    /**
     * Constructs a ReservoirSampler with target sample size {@code k}.
     *
     * @param k the maximum number of samples to retain (must be > 0)
     */
    public ReservoirSampler(int k) {
        this(k, new Random());
    }

    /**
     * Constructs a ReservoirSampler with target sample size {@code k} and a custom {@link Random} instance.
     * Useful for deterministic testing or custom pseudo-random generators.
     *
     * @param k      the maximum number of samples to retain (must be > 0)
     * @param random random number generator instance (not null)
     */
    public ReservoirSampler(int k, Random random) {
        if (k <= 0) {
            throw new IllegalArgumentException("Reservoir capacity k must be positive, got: " + k);
        }
        this.capacity = k;
        this.random = Objects.requireNonNull(random, "random cannot be null");
        this.reservoir = new ArrayList<>(k);
        this.totalSeen = 0L;
    }

    /**
     * Feeds an item into the reservoir sampler.
     * Automatically adjusts samples according to the Reservoir Algorithm R probability distribution.
     *
     * @param item the item to consider for inclusion
     */
    public synchronized void sample(T item) {
        totalSeen++;
        if (reservoir.size() < capacity) {
            reservoir.add(item);
        } else {
            // Pick a random number j in [0, totalSeen)
            long j = nextLongBounded(totalSeen);
            if (j < capacity) {
                reservoir.set((int) j, item);
            }
        }
    }

    /**
     * Returns an unmodifiable snapshot copy of the current sample pool.
     *
     * @return current sample list (size between 0 and k)
     */
    public synchronized List<T> getSamples() {
        return List.copyOf(reservoir);
    }

    /**
     * Returns the total number of items observed so far.
     *
     * @return total items seen
     */
    public synchronized long totalSeenCount() {
        return totalSeen;
    }

    /**
     * Returns the configured reservoir capacity k.
     *
     * @return sample capacity
     */
    public int capacity() {
        return capacity;
    }

    /**
     * Clears all collected samples and resets the seen counter to 0.
     */
    public synchronized void clear() {
        reservoir.clear();
        totalSeen = 0L;
    }

    /**
     * Generates a pseudorandom long value uniformly distributed between 0 (inclusive) and bound (exclusive).
     */
    private long nextLongBounded(long bound) {
        if (bound <= Integer.MAX_VALUE) {
            return random.nextInt((int) bound);
        }
        // Bit-shifting loop for 64-bit bounds
        long bits, val;
        do {
            bits = (random.nextLong() << 1) >>> 1;
            val = bits % bound;
        } while (bits - val + (bound - 1) < 0L);
        return val;
    }
}
