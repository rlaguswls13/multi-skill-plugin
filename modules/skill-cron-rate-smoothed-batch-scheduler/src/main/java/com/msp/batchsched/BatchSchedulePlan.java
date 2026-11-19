package com.msp.batchsched;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Immutable execution timeline plan produced by {@link RateSmoothedBatchScheduler}.
 */
public record BatchSchedulePlan<T>(
        Instant windowStart,
        Instant windowEnd,
        Duration windowDuration,
        Duration nominalInterval,
        int totalItems,
        List<ScheduledSlot<T>> slots
) {
    public BatchSchedulePlan {
        Objects.requireNonNull(windowStart, "windowStart must not be null");
        Objects.requireNonNull(windowEnd, "windowEnd must not be null");
        Objects.requireNonNull(windowDuration, "windowDuration must not be null");
        Objects.requireNonNull(nominalInterval, "nominalInterval must not be null");
        slots = List.copyOf(Objects.requireNonNull(slots, "slots must not be null"));
    }

    public int slotCount() {
        return slots.size();
    }

    public boolean isEmpty() {
        return slots.isEmpty();
    }

    /**
     * Returns the maximum number of items assigned to any single slot.
     */
    public int maxSlotSize() {
        int max = 0;
        for (ScheduledSlot<T> slot : slots) {
            if (slot.size() > max) {
                max = slot.size();
            }
        }
        return max;
    }

    /**
     * Returns all slots scheduled at or before {@code now}.
     */
    public List<ScheduledSlot<T>> dueSlots(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        return slots.stream()
                .filter(s -> !s.scheduledAt().isAfter(now))
                .toList();
    }

    /**
     * Returns all slots whose {@code scheduledAt} falls within {@code [fromInclusive, toExclusive)}.
     */
    public List<ScheduledSlot<T>> slotsInRange(Instant fromInclusive, Instant toExclusive) {
        Objects.requireNonNull(fromInclusive, "fromInclusive must not be null");
        Objects.requireNonNull(toExclusive, "toExclusive must not be null");
        return slots.stream()
                .filter(s -> !s.scheduledAt().isBefore(fromInclusive) && s.scheduledAt().isBefore(toExclusive))
                .toList();
    }
}
