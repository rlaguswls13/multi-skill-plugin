package com.msp.leakdetect;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.IntSupplier;

/**
 * Real-time collection and map memory leak detector.
 * Tracks size trends over time, detects monotonic growth, computes velocity, and emits alerts.
 */
public class MemoryLeakDetector implements AutoCloseable {

    private record Sample(Instant timestamp, int size) {
    }

    private static class WatchedTarget {
        private final String name;
        private final IntSupplier sizeSupplier;
        private final DetectionConfig config;
        private final Deque<Sample> history = new ArrayDeque<>();
        private int consecutiveGrowthCount = 0;
        private int initialSize = -1;
        private int sampleCount = 0;
        private double lastVelocity = 0.0;
        private Instant lastSampledAt;
        private int lastSize = 0;

        WatchedTarget(String name, IntSupplier sizeSupplier, DetectionConfig config) {
            this.name = name;
            this.sizeSupplier = sizeSupplier;
            this.config = config != null ? config : DetectionConfig.defaults();
        }

        synchronized List<LeakWarning> sample(Instant now) {
            int currentSize = sizeSupplier.getAsInt();
            sampleCount++;
            lastSampledAt = now;

            List<LeakWarning> warnings = new ArrayList<>();

            if (initialSize < 0) {
                initialSize = currentSize;
                lastSize = currentSize;
                history.addLast(new Sample(now, currentSize));
                return warnings;
            }

            int prevSize = lastSize;
            if (currentSize > prevSize) {
                consecutiveGrowthCount++;
            } else if (currentSize < prevSize) {
                consecutiveGrowthCount = 0;
            }

            // Calculate growth velocity
            Sample oldest = history.peekFirst();
            if (oldest != null) {
                long durationMillis = Duration.between(oldest.timestamp(), now).toMillis();
                if (durationMillis > 0) {
                    double durationSeconds = durationMillis / 1000.0;
                    lastVelocity = Math.max(0.0, (currentSize - oldest.size()) / durationSeconds);
                }
            }

            // Evaluation Rule 1: Monotonic Growth
            if (consecutiveGrowthCount >= config.consecutiveGrowthThreshold()) {
                warnings.add(new LeakWarning(
                        name,
                        WarningReason.MONOTONIC_GROWTH,
                        prevSize,
                        currentSize,
                        sampleCount,
                        consecutiveGrowthCount,
                        lastVelocity,
                        now,
                        String.format("Target '%s' grew monotonically across %d consecutive samples (%d -> %d items)",
                                name, consecutiveGrowthCount, prevSize, currentSize)
                ));
            }

            // Evaluation Rule 2: High Velocity
            if (config.velocityThresholdPerSecond() > 0 && lastVelocity >= config.velocityThresholdPerSecond()) {
                warnings.add(new LeakWarning(
                        name,
                        WarningReason.GROWTH_VELOCITY_EXCEEDED,
                        prevSize,
                        currentSize,
                        sampleCount,
                        consecutiveGrowthCount,
                        lastVelocity,
                        now,
                        String.format("Target '%s' growth velocity %.2f items/sec exceeded threshold %.2f items/sec",
                                name, lastVelocity, config.velocityThresholdPerSecond())
                ));
            }

            // Evaluation Rule 3: Absolute Capacity Exceeded
            if (config.maxCapacityThreshold() > 0 && currentSize >= config.maxCapacityThreshold()) {
                warnings.add(new LeakWarning(
                        name,
                        WarningReason.CAPACITY_LIMIT_EXCEEDED,
                        prevSize,
                        currentSize,
                        sampleCount,
                        consecutiveGrowthCount,
                        lastVelocity,
                        now,
                        String.format("Target '%s' size %d exceeded maximum capacity threshold %d",
                                name, currentSize, config.maxCapacityThreshold())
                ));
            }

            // Update history
            lastSize = currentSize;
            history.addLast(new Sample(now, currentSize));
            while (history.size() > config.maxHistorySamples()) {
                history.removeFirst();
            }

            return warnings;
        }

        synchronized TargetStats getStats() {
            return new TargetStats(
                    name,
                    lastSize,
                    initialSize >= 0 ? initialSize : lastSize,
                    sampleCount,
                    consecutiveGrowthCount,
                    lastVelocity,
                    lastSampledAt
            );
        }
    }

    private final Map<String, WatchedTarget> targets = new ConcurrentHashMap<>();
    private final List<LeakListener> listeners = new CopyOnWriteArrayList<>();
    private final List<LeakWarning> recentWarnings = new CopyOnWriteArrayList<>();
    private final int maxWarningHistory = 100;

    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> scheduledTask;

    public MemoryLeakDetector() {
    }

    public MemoryLeakDetector watch(String name, Collection<?> collection) {
        return watch(name, collection, DetectionConfig.defaults());
    }

    public MemoryLeakDetector watch(String name, Collection<?> collection, DetectionConfig config) {
        Objects.requireNonNull(collection, "collection must not be null");
        return watch(name, collection::size, config);
    }

    public MemoryLeakDetector watch(String name, Map<?, ?> map) {
        return watch(name, map, DetectionConfig.defaults());
    }

    public MemoryLeakDetector watch(String name, Map<?, ?> map, DetectionConfig config) {
        Objects.requireNonNull(map, "map must not be null");
        return watch(name, map::size, config);
    }

    public MemoryLeakDetector watch(String name, IntSupplier sizeSupplier) {
        return watch(name, sizeSupplier, DetectionConfig.defaults());
    }

    public MemoryLeakDetector watch(String name, IntSupplier sizeSupplier, DetectionConfig config) {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(sizeSupplier, "sizeSupplier must not be null");
        targets.put(name, new WatchedTarget(name, sizeSupplier, config));
        return this;
    }

    public boolean unwatch(String name) {
        return targets.remove(name) != null;
    }

    public MemoryLeakDetector addListener(LeakListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
        return this;
    }

    public boolean removeListener(LeakListener listener) {
        return listeners.remove(listener);
    }

    /**
     * Executes a manual sampling cycle over all watched collections.
     *
     * @return list of warnings emitted during this cycle
     */
    public List<LeakWarning> sample() {
        Instant now = Instant.now();
        List<LeakWarning> cycleWarnings = new ArrayList<>();

        for (WatchedTarget target : targets.values()) {
            List<LeakWarning> targetWarnings = target.sample(now);
            cycleWarnings.addAll(targetWarnings);
        }

        for (LeakWarning warning : cycleWarnings) {
            recordWarning(warning);
            for (LeakListener listener : listeners) {
                try {
                    listener.onLeakWarning(warning);
                } catch (Exception ignored) {
                }
            }
        }

        return cycleWarnings;
    }

    private void recordWarning(LeakWarning warning) {
        recentWarnings.add(warning);
        while (recentWarnings.size() > maxWarningHistory) {
            recentWarnings.remove(0);
        }
    }

    /**
     * Starts periodic sampling using a daemon scheduled thread.
     */
    public synchronized void start(Duration interval) {
        Objects.requireNonNull(interval, "interval must not be null");
        stop();
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "msp-leak-detector-scheduler");
            t.setDaemon(true);
            return t;
        });

        long intervalMs = Math.max(10, interval.toMillis());
        scheduledTask = scheduler.scheduleAtFixedRate(this::sample, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Stops periodic sampling.
     */
    public synchronized void stop() {
        if (scheduledTask != null) {
            scheduledTask.cancel(true);
            scheduledTask = null;
        }
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
    }

    public Optional<TargetStats> getStats(String name) {
        WatchedTarget target = targets.get(name);
        return target != null ? Optional.of(target.getStats()) : Optional.empty();
    }

    public Map<String, TargetStats> getAllStats() {
        Map<String, TargetStats> statsMap = new LinkedHashMap<>();
        for (Map.Entry<String, WatchedTarget> entry : targets.entrySet()) {
            statsMap.put(entry.getKey(), entry.getValue().getStats());
        }
        return Collections.unmodifiableMap(statsMap);
    }

    public List<LeakWarning> getRecentWarnings() {
        return Collections.unmodifiableList(new ArrayList<>(recentWarnings));
    }

    public void clearWarnings() {
        recentWarnings.clear();
    }

    @Override
    public void close() {
        stop();
        targets.clear();
        listeners.clear();
        recentWarnings.clear();
    }
}
