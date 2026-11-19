package com.msp.priorexec;

import java.time.Duration;
import java.time.Instant;

/**
 * Exception thrown when a queued task exceeds its Time-To-Live (TTL) deadline
 * before a worker thread starts executing it.
 */
public class TaskExpiredException extends RuntimeException {

    private final int priority;
    private final Instant submissionTime;
    private final Duration ttl;
    private final Duration waitedDuration;

    public TaskExpiredException(int priority, Instant submissionTime, Duration ttl, Duration waitedDuration) {
        super(String.format("Task with priority %d expired in queue: waited %d ms (TTL: %d ms)",
                priority, waitedDuration.toMillis(), ttl.toMillis()));
        this.priority = priority;
        this.submissionTime = submissionTime;
        this.ttl = ttl;
        this.waitedDuration = waitedDuration;
    }

    public int getPriority() {
        return priority;
    }

    public Instant getSubmissionTime() {
        return submissionTime;
    }

    public Duration getTtl() {
        return ttl;
    }

    public Duration getWaitedDuration() {
        return waitedDuration;
    }
}
