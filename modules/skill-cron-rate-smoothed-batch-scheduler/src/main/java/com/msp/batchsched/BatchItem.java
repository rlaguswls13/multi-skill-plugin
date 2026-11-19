package com.msp.batchsched;

import java.util.Objects;

/**
 * A single unit of work to be scheduled within a rate-smoothed batch window.
 *
 * @param key      logical grouping/routing key (e.g. tenant ID, target host, partition)
 * @param payload  arbitrary domain payload
 * @param priority scheduling priority (higher values are scheduled earlier when priority strategies are used)
 * @param <T>      payload type
 */
public record BatchItem<T>(String key, T payload, int priority) {

    public BatchItem {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(payload, "payload must not be null");
    }

    public static <T> BatchItem<T> of(String key, T payload) {
        return new BatchItem<>(key, payload, 0);
    }

    public static <T> BatchItem<T> of(String key, T payload, int priority) {
        return new BatchItem<>(key, payload, priority);
    }
}
