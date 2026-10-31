package com.msp.cronmerge;

import java.time.Duration;
import java.util.Objects;

/**
 * Definition of a scheduled job with its cron expression and expected execution duration.
 *
 * @param name              job name identifier
 * @param cronExpression    standard 5-field cron expression ("min hour dom month dow")
 * @param executionDuration estimated runtime duration of the job
 */
public record ScheduledJob(String name, String cronExpression, Duration executionDuration) {

    public ScheduledJob {
        Objects.requireNonNull(name, "Job name cannot be null");
        Objects.requireNonNull(cronExpression, "Cron expression cannot be null");
        Objects.requireNonNull(executionDuration, "Execution duration cannot be null");
        if (executionDuration.isNegative()) {
            throw new IllegalArgumentException("Execution duration cannot be negative: " + executionDuration);
        }
    }
}
