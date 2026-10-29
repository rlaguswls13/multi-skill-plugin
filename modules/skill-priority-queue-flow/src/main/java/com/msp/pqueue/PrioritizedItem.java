package com.msp.pqueue;

import java.util.Objects;

/**
 * Envelope holding an item, its assigned priority, and sequence number.
 *
 * @param <T> item type
 */
public record PrioritizedItem<T>(
        T item,
        Priority priority,
        long sequenceNumber,
        long enqueueTimeMillis
) {
    public PrioritizedItem {
        Objects.requireNonNull(priority, "priority must not be null");
    }
}
