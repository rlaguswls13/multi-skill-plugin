package com.msp.counter;

import java.util.Objects;
import java.util.concurrent.atomic.LongAdder;

/**
 * Ultra-high concurrency striped counter.
 * <p>
 * Distributes atomic increment/decrement operations across an array of {@link LongAdder}
 * instances using thread-affinity hashing. This drastically reduces CPU cache-line contention
 * and CAS retry storms under massive parallel workloads (e.g. hundreds of concurrent threads).
 * </p>
 */
public class StripedCounter extends Number {

    private static final int DEFAULT_MIN_STRIPES = 4;
    private static final int DEFAULT_MAX_STRIPES = 1024;

    private final LongAdder[] stripes;
    private final int mask;

    /**
     * Creates a {@link StripedCounter} with default stripe count based on available CPU cores.
     */
    public StripedCounter() {
        this(defaultStripeCount());
    }

    /**
     * Creates a {@link StripedCounter} with specified stripe count.
     * The count is rounded up to the nearest power of two.
     *
     * @param requestedStripes desired stripe count (must be &gt;= 1)
     */
    public StripedCounter(int requestedStripes) {
        if (requestedStripes <= 0) {
            throw new IllegalArgumentException("Stripe count must be positive: " + requestedStripes);
        }
        int capacity = nextPowerOfTwo(requestedStripes);
        this.stripes = new LongAdder[capacity];
        for (int i = 0; i < capacity; i++) {
            this.stripes[i] = new LongAdder();
        }
        this.mask = capacity - 1;
    }

    /**
     * Increments the counter by 1.
     */
    public void increment() {
        add(1L);
    }

    /**
     * Increments the counter by specified amount.
     *
     * @param amount amount to add
     */
    public void increment(long amount) {
        add(amount);
    }

    /**
     * Decrements the counter by 1.
     */
    public void decrement() {
        add(-1L);
    }

    /**
     * Decrements the counter by specified amount.
     *
     * @param amount amount to subtract
     */
    public void decrement(long amount) {
        add(-amount);
    }

    /**
     * Adds the given value to the counter.
     *
     * @param x value to add
     */
    public void add(long x) {
        int idx = getStripeIndex();
        stripes[idx].add(x);
    }

    /**
     * Returns the current total sum across all stripes.
     *
     * @return current sum
     */
    public long sum() {
        long total = 0;
        for (LongAdder adder : stripes) {
            total += adder.sum();
        }
        return total;
    }

    /**
     * Resets all stripes in this counter to zero.
     */
    public void reset() {
        for (LongAdder adder : stripes) {
            adder.reset();
        }
    }

    /**
     * Returns the current total sum and atomically resets all stripes to zero.
     *
     * @return sum before reset
     */
    public long sumThenReset() {
        long total = 0;
        for (LongAdder adder : stripes) {
            total += adder.sumThenReset();
        }
        return total;
    }

    /**
     * Returns the number of stripes backing this counter.
     *
     * @return stripe count
     */
    public int getStripeCount() {
        return stripes.length;
    }

    @Override
    public int intValue() {
        return (int) sum();
    }

    @Override
    public long longValue() {
        return sum();
    }

    @Override
    public float floatValue() {
        return (float) sum();
    }

    @Override
    public double doubleValue() {
        return (double) sum();
    }

    @Override
    public String toString() {
        return Long.toString(sum());
    }

    private int getStripeIndex() {
        long id = Thread.currentThread().threadId();
        // Wang-Jenkins 64-bit mix hash
        id = (id ^ (id >>> 30)) * 0xbf58476d1ce4e5b9L;
        id = (id ^ (id >>> 27)) * 0x94d049bb133111ebL;
        id = id ^ (id >>> 31);
        return (int) (id & mask);
    }

    private static int defaultStripeCount() {
        int cores = Runtime.getRuntime().availableProcessors();
        int calculated = cores * 4;
        return Math.clamp(calculated, DEFAULT_MIN_STRIPES, DEFAULT_MAX_STRIPES);
    }

    private static int nextPowerOfTwo(int n) {
        if (n <= 1) return 1;
        if (n >= (1 << 30)) return 1 << 30;
        return Integer.highestOneBit(n - 1) << 1;
    }
}
