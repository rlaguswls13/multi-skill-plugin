package com.msp.metrics;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Thread-safe metric accumulator calculating histogram percentiles and sliding-window TPS.
 */
class MetricTracker {

    private static final int DEFAULT_RESERVOIR_SIZE = 2048;
    private static final int TPS_WINDOW_SECONDS = 5;
    private static final int TPS_BUCKETS = 10;

    private final String name;
    private final int reservoirSize;
    private final long[] reservoir;
    private final AtomicLong reservoirCounter = new AtomicLong(0);

    private final LongAdder totalCount = new LongAdder();
    private final LongAdder totalNanos = new LongAdder();
    private final AtomicLong minNanos = new AtomicLong(Long.MAX_VALUE);
    private final AtomicLong maxNanos = new AtomicLong(0);

    // Sliding window TPS buckets
    private final long[] bucketSeconds = new long[TPS_BUCKETS];
    private final LongAdder[] bucketCounts = new LongAdder[TPS_BUCKETS];

    public MetricTracker(String name) {
        this(name, DEFAULT_RESERVOIR_SIZE);
    }

    public MetricTracker(String name, int reservoirSize) {
        this.name = name;
        this.reservoirSize = reservoirSize;
        this.reservoir = new long[reservoirSize];
        for (int i = 0; i < TPS_BUCKETS; i++) {
            this.bucketCounts[i] = new LongAdder();
        }
    }

    public void record(long nanos) {
        if (nanos < 0) {
            nanos = 0;
        }

        totalCount.increment();
        totalNanos.add(nanos);
        minNanos.accumulateAndGet(nanos, Math::min);
        maxNanos.accumulateAndGet(nanos, Math::max);

        // Store into reservoir
        int index = (int) ((reservoirCounter.getAndIncrement() & 0x7FFFFFFFFFFFFFFFL) % reservoirSize);
        reservoir[index] = nanos;

        // Track TPS
        recordTps();
    }

    private synchronized void recordTps() {
        long currentSec = System.currentTimeMillis() / 1000;
        int idx = (int) (currentSec % TPS_BUCKETS);
        if (bucketSeconds[idx] != currentSec) {
            bucketSeconds[idx] = currentSec;
            bucketCounts[idx].reset();
        }
        bucketCounts[idx].increment();
    }

    public synchronized double calculateCurrentTps() {
        long currentSec = System.currentTimeMillis() / 1000;
        long windowStart = currentSec - TPS_WINDOW_SECONDS + 1;
        long sum = 0;

        for (int i = 0; i < TPS_BUCKETS; i++) {
            if (bucketSeconds[i] >= windowStart && bucketSeconds[i] <= currentSec) {
                sum += bucketCounts[i].sum();
            }
        }
        return (double) sum / TPS_WINDOW_SECONDS;
    }

    public MetricSnapshot snapshot() {
        long count = totalCount.sum();
        if (count == 0) {
            return new MetricSnapshot(name, 0, 0, 0, 0, 0.0, 0, 0, 0, 0, 0.0);
        }

        long sumNanos = totalNanos.sum();
        long min = minNanos.get();
        if (min == Long.MAX_VALUE) {
            min = 0;
        }
        long max = maxNanos.get();
        double mean = (double) sumNanos / count;

        int sampleCount = (int) Math.min(count, reservoirSize);
        long[] copy = new long[sampleCount];
        System.arraycopy(reservoir, 0, copy, 0, sampleCount);
        Arrays.sort(copy);

        long p50 = computePercentile(copy, 50.0);
        long p90 = computePercentile(copy, 90.0);
        long p95 = computePercentile(copy, 95.0);
        long p99 = computePercentile(copy, 99.0);

        double tps = calculateCurrentTps();

        return new MetricSnapshot(
                name,
                count,
                sumNanos,
                min,
                max,
                mean,
                p50,
                p90,
                p95,
                p99,
                tps
        );
    }

    private static long computePercentile(long[] sorted, double percentile) {
        if (sorted.length == 0) {
            return 0;
        }
        if (sorted.length == 1) {
            return sorted[0];
        }

        double rank = (percentile / 100.0) * (sorted.length - 1);
        int lower = (int) Math.floor(rank);
        int upper = (int) Math.ceil(rank);

        lower = Math.max(0, Math.min(sorted.length - 1, lower));
        upper = Math.max(0, Math.min(sorted.length - 1, upper));

        if (lower == upper) {
            return sorted[lower];
        }

        double fraction = rank - lower;
        return (long) (sorted[lower] + fraction * (sorted[upper] - sorted[lower]));
    }
}
