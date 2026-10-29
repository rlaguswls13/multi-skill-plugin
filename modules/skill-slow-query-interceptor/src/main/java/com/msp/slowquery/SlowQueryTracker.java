package com.msp.slowquery;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Thread-safe tracker for detecting, logging, and aggregating slow database queries.
 */
public final class SlowQueryTracker {

    private volatile Duration threshold;
    private final ConcurrentHashMap<String, StatsAccumulator> statsMap = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<SlowQueryListener> listeners = new CopyOnWriteArrayList<>();

    /**
     * Creates a SlowQueryTracker with default threshold of 500 milliseconds.
     */
    public SlowQueryTracker() {
        this(Duration.ofMillis(500));
    }

    /**
     * Creates a SlowQueryTracker with a specified slow query threshold.
     */
    public SlowQueryTracker(Duration threshold) {
        this.threshold = Objects.requireNonNull(threshold, "threshold must not be null");
    }

    /**
     * Gets the configured slow query threshold duration.
     */
    public Duration getThreshold() {
        return threshold;
    }

    /**
     * Updates the slow query threshold duration.
     */
    public void setThreshold(Duration threshold) {
        this.threshold = Objects.requireNonNull(threshold, "threshold must not be null");
    }

    /**
     * Registers a callback listener to be invoked when a slow query occurs.
     */
    public void addListener(SlowQueryListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    /**
     * Removes a previously registered callback listener.
     */
    public void removeListener(SlowQueryListener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    /**
     * Records a query execution with the given duration.
     *
     * @param rawSql   the executed SQL statement
     * @param duration execution duration
     */
    public void recordQuery(String rawSql, Duration duration) {
        if (rawSql == null || duration == null) return;

        Instant now = Instant.now();
        String normalized = SqlNormalizer.normalize(rawSql);
        boolean isSlow = duration.compareTo(threshold) >= 0;

        statsMap.computeIfAbsent(normalized, StatsAccumulator::new).record(duration, isSlow, now);

        if (isSlow && !listeners.isEmpty()) {
            SlowQueryAlert alert = new SlowQueryAlert(rawSql, normalized, duration, threshold, now);
            for (SlowQueryListener listener : listeners) {
                try {
                    listener.onSlowQuery(alert);
                } catch (Throwable ignored) {
                    // Prevent listener exceptions from breaking query execution flow
                }
            }
        }
    }

    /**
     * Records a query execution with duration in milliseconds.
     */
    public void recordQuery(String rawSql, long durationMs) {
        recordQuery(rawSql, Duration.ofMillis(durationMs));
    }

    /**
     * Executes a Callable and measures its execution duration.
     */
    public <T> T execute(String rawSql, Callable<T> callable) throws Exception {
        Objects.requireNonNull(callable, "callable must not be null");
        long start = System.nanoTime();
        try {
            return callable.call();
        } finally {
            long elapsedNanos = System.nanoTime() - start;
            recordQuery(rawSql, Duration.ofNanos(elapsedNanos));
        }
    }

    /**
     * Executes a Runnable and measures its execution duration.
     */
    public void execute(String rawSql, Runnable runnable) {
        Objects.requireNonNull(runnable, "runnable must not be null");
        long start = System.nanoTime();
        try {
            runnable.run();
        } finally {
            long elapsedNanos = System.nanoTime() - start;
            recordQuery(rawSql, Duration.ofNanos(elapsedNanos));
        }
    }

    /**
     * Starts an AutoCloseable timer for convenient query measurement.
     * <pre>{@code
     * try (QueryTimer timer = tracker.start("SELECT * FROM tbl")) {
     *     // execute query
     * }
     * }</pre>
     */
    public QueryTimer start(String rawSql) {
        return new QueryTimer(rawSql);
    }

    /**
     * Returns statistics for all recorded normalized queries.
     */
    public List<QueryStats> getAllStats() {
        List<QueryStats> list = new ArrayList<>(statsMap.size());
        for (StatsAccumulator acc : statsMap.values()) {
            list.add(acc.snapshot());
        }
        return list;
    }

    /**
     * Returns top slowest queries ordered by average execution duration descending.
     */
    public List<QueryStats> getSlowestQueries(int limit) {
        if (limit <= 0) return Collections.emptyList();
        return getAllStats().stream()
                .sorted(Comparator.comparing(QueryStats::avgDuration).reversed())
                .limit(limit)
                .toList();
    }

    /**
     * Retrieves statistics for a specific SQL query (either raw or normalized).
     */
    public Optional<QueryStats> getStats(String rawOrNormalizedSql) {
        if (rawOrNormalizedSql == null) return Optional.empty();
        String normalized = SqlNormalizer.normalize(rawOrNormalizedSql);
        StatsAccumulator acc = statsMap.get(normalized);
        return acc != null ? Optional.of(acc.snapshot()) : Optional.empty();
    }

    /**
     * Clears all recorded query statistics.
     */
    public void reset() {
        statsMap.clear();
    }

    /**
     * AutoCloseable timer helper.
     */
    public final class QueryTimer implements AutoCloseable {
        private final String rawSql;
        private final long startNanos;
        private boolean closed = false;

        private QueryTimer(String rawSql) {
            this.rawSql = rawSql;
            this.startNanos = System.nanoTime();
        }

        public void stop() {
            close();
        }

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                long elapsedNanos = System.nanoTime() - startNanos;
                recordQuery(rawSql, Duration.ofNanos(elapsedNanos));
            }
        }
    }

    private static final class StatsAccumulator {
        private final String normalizedSql;
        private long count;
        private long slowCount;
        private long totalNanos;
        private long minNanos = Long.MAX_VALUE;
        private long maxNanos = 0;
        private Instant lastExecutedAt;

        StatsAccumulator(String normalizedSql) {
            this.normalizedSql = normalizedSql;
            this.lastExecutedAt = Instant.now();
        }

        synchronized void record(Duration duration, boolean isSlow, Instant now) {
            long nanos = duration.toNanos();
            count++;
            if (isSlow) slowCount++;
            totalNanos += nanos;
            if (nanos < minNanos) minNanos = nanos;
            if (nanos > maxNanos) maxNanos = nanos;
            lastExecutedAt = now;
        }

        synchronized QueryStats snapshot() {
            return new QueryStats(
                    normalizedSql,
                    count,
                    slowCount,
                    Duration.ofNanos(totalNanos),
                    Duration.ofNanos(count > 0 ? minNanos : 0),
                    Duration.ofNanos(maxNanos),
                    lastExecutedAt
            );
        }
    }
}
