package com.msp.pqueue;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Priority-based (P0-P3) in-memory work queue with fair scheduling and anti-starvation.
 * <p>
 * Ensures higher-priority items (CRITICAL, HIGH) process first while using deficit weighted
 * quotas (default ratio 8:4:2:1) to prevent total starvation of lower-priority tasks (NORMAL, LOW).
 * </p>
 *
 * @param <T> work item type
 */
public class PriorityWorkQueue<T> {

    private static final int PRIORITY_COUNT = Priority.values().length;

    private final Queue<PrioritizedItem<T>>[] queues;
    private final int[] weights;
    private final int[] remainingQuotas;
    private final AtomicLong sequenceCounter = new AtomicLong(0);

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition notEmpty = lock.newCondition();
    private int totalSize = 0;
    private boolean fairSchedulingEnabled = true;

    /**
     * Creates a {@link PriorityWorkQueue} with default fair quota weights (8:4:2:1).
     */
    @SuppressWarnings("unchecked")
    public PriorityWorkQueue() {
        this.queues = new Queue[PRIORITY_COUNT];
        this.weights = new int[PRIORITY_COUNT];
        this.remainingQuotas = new int[PRIORITY_COUNT];

        for (Priority p : Priority.values()) {
            this.queues[p.level()] = new ArrayDeque<>();
            this.weights[p.level()] = p.defaultWeight();
            this.remainingQuotas[p.level()] = p.defaultWeight();
        }
    }

    /**
     * Creates a {@link PriorityWorkQueue} with custom quota weights for [CRITICAL, HIGH, NORMAL, LOW].
     */
    @SuppressWarnings("unchecked")
    public PriorityWorkQueue(int criticalWeight, int highWeight, int normalWeight, int lowWeight) {
        if (criticalWeight <= 0 || highWeight <= 0 || normalWeight <= 0 || lowWeight <= 0) {
            throw new IllegalArgumentException("Weights must be positive integers");
        }
        this.queues = new Queue[PRIORITY_COUNT];
        this.weights = new int[]{criticalWeight, highWeight, normalWeight, lowWeight};
        this.remainingQuotas = Arrays.copyOf(this.weights, PRIORITY_COUNT);

        for (int i = 0; i < PRIORITY_COUNT; i++) {
            this.queues[i] = new ArrayDeque<>();
        }
    }

    /**
     * Submits an item with default NORMAL (P2) priority.
     *
     * @param item item to enqueue
     */
    public void submit(T item) {
        submit(item, Priority.NORMAL);
    }

    /**
     * Submits an item with specified priority level.
     *
     * @param item     item to enqueue
     * @param priority priority level
     */
    public void submit(T item, Priority priority) {
        Objects.requireNonNull(priority, "priority must not be null");
        long seq = sequenceCounter.getAndIncrement();
        PrioritizedItem<T> envelope = new PrioritizedItem<>(item, priority, seq, System.currentTimeMillis());

        lock.lock();
        try {
            queues[priority.level()].add(envelope);
            totalSize++;
            notEmpty.signal();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Non-blocking poll for the next scheduled item.
     *
     * @return next item according to fair priority scheduling, or null if empty
     */
    public T poll() {
        lock.lock();
        try {
            return pollInternal();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Polls for the next scheduled item, waiting up to the specified timeout if empty.
     *
     * @param timeout maximum duration to wait
     * @return next item, or null if timeout elapsed
     * @throws InterruptedException if interrupted while waiting
     */
    public T poll(Duration timeout) throws InterruptedException {
        Objects.requireNonNull(timeout, "timeout must not be null");
        long nanos = timeout.toNanos();
        lock.lockInterruptibly();
        try {
            while (totalSize == 0) {
                if (nanos <= 0) {
                    return null;
                }
                nanos = notEmpty.awaitNanos(nanos);
            }
            return pollInternal();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Takes the next scheduled item, blocking until an item becomes available.
     *
     * @return next item
     * @throws InterruptedException if interrupted while waiting
     */
    public T take() throws InterruptedException {
        lock.lockInterruptibly();
        try {
            while (totalSize == 0) {
                notEmpty.await();
            }
            return pollInternal();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns the total number of items currently in the queue across all priority levels.
     */
    public int size() {
        lock.lock();
        try {
            return totalSize;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns the number of items queued at the specified priority level.
     */
    public int size(Priority priority) {
        Objects.requireNonNull(priority, "priority must not be null");
        lock.lock();
        try {
            return queues[priority.level()].size();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns true if the queue contains no items.
     */
    public boolean isEmpty() {
        lock.lock();
        try {
            return totalSize == 0;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Clears all items from the queue.
     */
    public void clear() {
        lock.lock();
        try {
            for (Queue<PrioritizedItem<T>> q : queues) {
                q.clear();
            }
            totalSize = 0;
            replenishQuotas();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Toggles whether fair scheduling (anti-starvation weighted deficit) or strict priority is used.
     */
    public void setFairSchedulingEnabled(boolean enabled) {
        lock.lock();
        try {
            this.fairSchedulingEnabled = enabled;
        } finally {
            lock.unlock();
        }
    }

    public boolean isFairSchedulingEnabled() {
        lock.lock();
        try {
            return fairSchedulingEnabled;
        } finally {
            lock.unlock();
        }
    }

    private T pollInternal() {
        if (totalSize == 0) {
            return null;
        }

        if (!fairSchedulingEnabled) {
            // Strict priority drain
            for (int i = 0; i < PRIORITY_COUNT; i++) {
                Queue<PrioritizedItem<T>> q = queues[i];
                if (!q.isEmpty()) {
                    totalSize--;
                    return q.poll().item();
                }
            }
            return null;
        }

        // Fair scheduling with weighted quota
        // 1. Find highest priority queue with remaining quota
        for (int i = 0; i < PRIORITY_COUNT; i++) {
            if (!queues[i].isEmpty() && remainingQuotas[i] > 0) {
                remainingQuotas[i]--;
                totalSize--;
                return queues[i].poll().item();
            }
        }

        // 2. All non-empty queues exhausted quota in this round -> replenish quotas
        replenishQuotas();

        // 3. Re-evaluate
        for (int i = 0; i < PRIORITY_COUNT; i++) {
            if (!queues[i].isEmpty() && remainingQuotas[i] > 0) {
                remainingQuotas[i]--;
                totalSize--;
                return queues[i].poll().item();
            }
        }

        return null;
    }

    private void replenishQuotas() {
        System.arraycopy(weights, 0, remainingQuotas, 0, PRIORITY_COUNT);
    }
}
