package com.msp.priorexec;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.LongAdder;

/**
 * Internal prioritized task holder with priority, FIFO tie-breaking sequence, and TTL deadline.
 *
 * @param <T> task result type
 */
final class PriorityTask<T> implements Runnable, Comparable<Runnable> {

    private final Callable<T> callable;
    private final CompletableFuture<T> future;
    private final int priority;
    private final long sequenceNumber;
    private final Instant submissionTime;
    private final Duration ttl;
    private final LongAdder completedCounter;
    private final LongAdder expiredCounter;

    PriorityTask(Callable<T> callable, CompletableFuture<T> future, int priority, long sequenceNumber,
                 Duration ttl, LongAdder completedCounter, LongAdder expiredCounter) {
        this.callable = Objects.requireNonNull(callable, "callable must not be null");
        this.future = Objects.requireNonNull(future, "future must not be null");
        this.priority = Math.clamp(priority, 1, 10);
        this.sequenceNumber = sequenceNumber;
        this.submissionTime = Instant.now();
        this.ttl = ttl;
        this.completedCounter = completedCounter;
        this.expiredCounter = expiredCounter;
    }

    public int getPriority() {
        return priority;
    }

    public long getSequenceNumber() {
        return sequenceNumber;
    }

    public Instant getSubmissionTime() {
        return submissionTime;
    }

    public Duration getTtl() {
        return ttl;
    }

    public CompletableFuture<T> getFuture() {
        return future;
    }

    public boolean isExpired() {
        if (ttl == null) {
            return false;
        }
        Duration elapsed = Duration.between(submissionTime, Instant.now());
        return elapsed.compareTo(ttl) > 0;
    }

    @Override
    public void run() {
        if (future.isDone()) {
            return;
        }

        if (isExpired()) {
            if (expiredCounter != null) {
                expiredCounter.increment();
            }
            Duration elapsed = Duration.between(submissionTime, Instant.now());
            future.completeExceptionally(new TaskExpiredException(priority, submissionTime, ttl, elapsed));
            return;
        }

        try {
            T result = callable.call();
            if (completedCounter != null) {
                completedCounter.increment();
            }
            future.complete(result);
        } catch (Throwable t) {
            if (completedCounter != null) {
                completedCounter.increment();
            }
            future.completeExceptionally(t);
        }
    }

    @Override
    public int compareTo(Runnable o) {
        if (o instanceof PriorityTask<?> other) {
            // Higher priority first
            int cmp = Integer.compare(other.priority, this.priority);
            if (cmp != 0) {
                return cmp;
            }
            // Lower sequence number first (FIFO stability)
            return Long.compare(this.sequenceNumber, other.sequenceNumber);
        }
        return 0;
    }
}
