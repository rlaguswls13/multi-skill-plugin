package com.msp.fallback;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 최종 실패한 작업과 원인을 임시 격리 및 보관하는 인메모리 Dead-Letter Queue.
 */
public final class DeadLetterQueue<T> {

    private final int maxCapacity;
    private final ConcurrentLinkedDeque<DeadLetterItem<T>> queue = new ConcurrentLinkedDeque<>();

    public DeadLetterQueue() {
        this(1000);
    }

    public DeadLetterQueue(int maxCapacity) {
        if (maxCapacity <= 0) {
            throw new IllegalArgumentException("maxCapacity must be positive: " + maxCapacity);
        }
        this.maxCapacity = maxCapacity;
    }

    public void record(String identifier, T payload, Throwable cause) {
        while (queue.size() >= maxCapacity) {
            queue.pollFirst();
        }
        queue.addLast(new DeadLetterItem<>(identifier, payload, cause, Instant.now()));
    }

    public int size() {
        return queue.size();
    }

    public List<DeadLetterItem<T>> drainAll() {
        List<DeadLetterItem<T>> list = new ArrayList<>();
        DeadLetterItem<T> item;
        while ((item = queue.pollFirst()) != null) {
            list.add(item);
        }
        return list;
    }

    public List<DeadLetterItem<T>> snapshot() {
        return new ArrayList<>(queue);
    }
}
