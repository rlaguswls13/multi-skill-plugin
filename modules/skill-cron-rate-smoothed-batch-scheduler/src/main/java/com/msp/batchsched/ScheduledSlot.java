package com.msp.batchsched;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * A single scheduled execution slot within a {@link BatchSchedulePlan}.
 *
 * @param slotIndex      zero-based slot index
 * @param scheduledAt    effective execution timestamp (nominal + jitter, clamped to window)
 * @param nominalAt      uniform grid timestamp before jitter
 * @param jitterOffset   signed jitter delta applied ({@code scheduledAt - nominalAt})
 * @param maxConcurrency configured concurrency limit for executing items in this slot
 * @param items          immutable list of batch items assigned to this slot
 * @param <T>            payload type
 */
public record ScheduledSlot<T>(
        int slotIndex,
        Instant scheduledAt,
        Instant nominalAt,
        Duration jitterOffset,
        int maxConcurrency,
        List<BatchItem<T>> items
) {
    public ScheduledSlot {
        if (slotIndex < 0) {
            throw new IllegalArgumentException("slotIndex must be >= 0: " + slotIndex);
        }
        Objects.requireNonNull(scheduledAt, "scheduledAt must not be null");
        Objects.requireNonNull(nominalAt, "nominalAt must not be null");
        Objects.requireNonNull(jitterOffset, "jitterOffset must not be null");
        if (maxConcurrency <= 0) {
            throw new IllegalArgumentException("maxConcurrency must be > 0: " + maxConcurrency);
        }
        items = List.copyOf(Objects.requireNonNull(items, "items must not be null"));
    }

    public int size() {
        return items.size();
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }
}
