package com.msp.cron;

import java.time.ZonedDateTime;

/**
 * Handle to a scheduled cron task allowing status checks and cancellation.
 */
public interface ScheduledTask {

    /**
     * Unique identifier of this scheduled task.
     */
    String taskId();

    /**
     * Human-readable name or description of the task.
     */
    String name();

    /**
     * The cron expression driving this task.
     */
    CronExpression expression();

    /**
     * Estimated next execution time, or null if cancelled/finished.
     */
    ZonedDateTime nextExecutionTime();

    /**
     * Cancels future executions of this task.
     */
    void cancel();

    /**
     * Checks if this task has been cancelled.
     */
    boolean isCancelled();
}
