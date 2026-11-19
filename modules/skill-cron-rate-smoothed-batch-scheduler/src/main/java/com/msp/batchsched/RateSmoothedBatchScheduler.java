package com.msp.batchsched;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Stateless, thread-safe rate-smoothed batch scheduler that distributes {@code N} batch items
 * evenly across a target time window {@code [start, start + window]} into balanced execution slots.
 *
 * <p>Features:
 * <ul>
 *   <li>Balanced slot sizing (e.g. 10 items with {@code maxBatchSize=4} produces 3 slots of sizes 4, 3, 3)</li>
 *   <li>Uniform nominal spacing across the window with bounded, reproducible deterministic jitter</li>
 *   <li>Priority ordering and round-robin key interleaving to prevent downstream tenant hot-spots</li>
 * </ul>
 */
public final class RateSmoothedBatchScheduler {

    private RateSmoothedBatchScheduler() {
    }

    /**
     * Builds a rate-smoothed schedule using {@link BatchScheduleConfig#defaults()}.
     */
    public static <T> BatchSchedulePlan<T> plan(List<BatchItem<T>> items, Instant start, Duration window) {
        return plan(items, start, window, BatchScheduleConfig.defaults());
    }

    /**
     * Builds a rate-smoothed schedule for raw payloads (assigned sequential string keys {@code "0"}, {@code "1"}, ...).
     */
    public static <T> BatchSchedulePlan<T> planRaw(
            List<T> payloads,
            Instant start,
            Duration window,
            BatchScheduleConfig config
    ) {
        Objects.requireNonNull(payloads, "payloads must not be null");
        List<BatchItem<T>> items = new ArrayList<>(payloads.size());
        for (int i = 0; i < payloads.size(); i++) {
            items.add(BatchItem.of(Integer.toString(i), payloads.get(i)));
        }
        return plan(items, start, window, config);
    }

    /**
     * Builds a rate-smoothed {@link BatchSchedulePlan} distributing {@code items} across {@code [start, start + window]}.
     */
    public static <T> BatchSchedulePlan<T> plan(
            List<BatchItem<T>> items,
            Instant start,
            Duration window,
            BatchScheduleConfig config
    ) {
        Objects.requireNonNull(items, "items must not be null");
        Objects.requireNonNull(start, "start must not be null");
        Objects.requireNonNull(window, "window must not be null");
        Objects.requireNonNull(config, "config must not be null");
        if (window.isNegative()) {
            throw new IllegalArgumentException("window must not be negative: " + window);
        }

        Instant windowEnd = start.plus(window);
        if (items.isEmpty()) {
            return new BatchSchedulePlan<>(start, windowEnd, window, Duration.ZERO, 0, List.of());
        }

        List<BatchItem<T>> ordered = orderItems(items, config.spreadStrategy());
        int totalItems = ordered.size();
        int slotCount = (totalItems + config.maxBatchSize() - 1) / config.maxBatchSize();

        Duration nominalInterval = slotCount == 1 ? window : window.dividedBy(slotCount);
        double intervalNanos = toDoubleNanos(nominalInterval);

        int baseSize = totalItems / slotCount;
        int remainder = totalItems % slotCount;

        List<ScheduledSlot<T>> slots = new ArrayList<>(slotCount);
        int cursor = 0;

        for (int slotIdx = 0; slotIdx < slotCount; slotIdx++) {
            int size = baseSize + (slotIdx < remainder ? 1 : 0);
            List<BatchItem<T>> slice = ordered.subList(cursor, cursor + size);
            cursor += size;

            Instant nominalAt = slotCount == 1
                    ? start
                    : start.plus(scaleDuration(window, slotIdx, slotCount));

            Instant scheduledAt = nominalAt;
            if (config.jitterRatio() > 0.0 && intervalNanos > 0.0) {
                double unit = deterministicUnitSigned(config.jitterSeed(), slotIdx);
                long jitterNanos = Math.round(unit * config.jitterRatio() * intervalNanos);
                Instant candidate = nominalAt.plusNanos(jitterNanos);
                if (candidate.isBefore(start)) {
                    candidate = start;
                } else if (candidate.isAfter(windowEnd)) {
                    candidate = windowEnd;
                }
                scheduledAt = candidate;
            }

            Duration actualJitter = Duration.between(nominalAt, scheduledAt);
            slots.add(new ScheduledSlot<>(
                    slotIdx,
                    scheduledAt,
                    nominalAt,
                    actualJitter,
                    config.maxConcurrency(),
                    slice
            ));
        }

        return new BatchSchedulePlan<>(start, windowEnd, window, nominalInterval, totalItems, slots);
    }

    private static <T> List<BatchItem<T>> orderItems(List<BatchItem<T>> items, SpreadStrategy strategy) {
        return switch (strategy) {
            case PRESERVE_ORDER -> new ArrayList<>(items);
            case PRIORITY_DESC -> {
                List<BatchItem<T>> copy = new ArrayList<>(items);
                copy.sort(Comparator.comparingInt(BatchItem<T>::priority).reversed());
                yield copy;
            }
            case ROUND_ROBIN_BY_KEY -> interleaveByKey(items);
            case PRIORITY_THEN_KEY_SPREAD -> {
                TreeMap<Integer, List<BatchItem<T>>> byPriority = new TreeMap<>(Comparator.reverseOrder());
                for (BatchItem<T> item : items) {
                    byPriority.computeIfAbsent(item.priority(), k -> new ArrayList<>()).add(item);
                }
                List<BatchItem<T>> result = new ArrayList<>(items.size());
                for (List<BatchItem<T>> tier : byPriority.values()) {
                    result.addAll(interleaveByKey(tier));
                }
                yield result;
            }
        };
    }

    private static <T> List<BatchItem<T>> interleaveByKey(List<BatchItem<T>> items) {
        Map<String, Deque<BatchItem<T>>> queues = new LinkedHashMap<>();
        for (BatchItem<T> item : items) {
            queues.computeIfAbsent(item.key(), k -> new ArrayDeque<>()).addLast(item);
        }
        List<BatchItem<T>> result = new ArrayList<>(items.size());
        while (result.size() < items.size()) {
            for (Deque<BatchItem<T>> queue : queues.values()) {
                if (!queue.isEmpty()) {
                    result.add(queue.removeFirst());
                }
            }
        }
        return result;
    }

    private static Duration scaleDuration(Duration window, int index, int count) {
        if (index == 0 || window.isZero()) {
            return Duration.ZERO;
        }
        // Use double-precision nanos/seconds scaling to avoid 64-bit overflow on large windows
        double totalNanos = toDoubleNanos(window);
        double scaledNanos = (totalNanos * index) / count;
        long seconds = (long) Math.floor(scaledNanos / 1_000_000_000.0);
        long nanos = Math.round(scaledNanos - (seconds * 1_000_000_000.0));
        return Duration.ofSeconds(seconds, nanos);
    }

    private static double toDoubleNanos(Duration d) {
        return d.getSeconds() * 1_000_000_000.0 + d.getNano();
    }

    /**
     * SplitMix64-based deterministic hash returning a double in {@code [-1.0, +1.0]}.
     */
    private static double deterministicUnitSigned(long seed, int slotIndex) {
        long z = seed + (slotIndex + 1L) * 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z = z ^ (z >>> 31);
        // Map top 53 bits to [0.0, 1.0) then [-1.0, +1.0]
        double unit01 = (z >>> 11) * 0x1.0p-53;
        return unit01 * 2.0 - 1.0;
    }
}
