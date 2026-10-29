package com.msp.slowquery;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Alert event emitted when an executed query exceeds the configured slow query threshold.
 *
 * @param rawSql        the original unnormalized SQL statement
 * @param normalizedSql the normalized parameterized SQL template
 * @param duration      actual execution duration
 * @param threshold     the configured threshold duration at time of execution
 * @param timestamp     timestamp when the execution finished
 */
public record SlowQueryAlert(
        String rawSql,
        String normalizedSql,
        Duration duration,
        Duration threshold,
        Instant timestamp
) {
    public SlowQueryAlert {
        Objects.requireNonNull(rawSql, "rawSql must not be null");
        Objects.requireNonNull(normalizedSql, "normalizedSql must not be null");
        Objects.requireNonNull(duration, "duration must not be null");
        Objects.requireNonNull(threshold, "threshold must not be null");
        Objects.requireNonNull(timestamp, "timestamp must not be null");
    }
}
