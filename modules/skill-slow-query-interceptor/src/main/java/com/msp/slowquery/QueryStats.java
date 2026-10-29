package com.msp.slowquery;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Aggregated execution statistics for a normalized SQL statement.
 *
 * @param normalizedSql  the parameterized SQL template
 * @param executionCount total number of executions
 * @param slowCount      number of executions that exceeded the slow query threshold
 * @param totalDuration  cumulative execution duration
 * @param minDuration    shortest observed execution duration
 * @param maxDuration    longest observed execution duration
 * @param lastExecutedAt timestamp of the most recent execution
 */
public record QueryStats(
        String normalizedSql,
        long executionCount,
        long slowCount,
        Duration totalDuration,
        Duration minDuration,
        Duration maxDuration,
        Instant lastExecutedAt
) {
    public QueryStats {
        Objects.requireNonNull(normalizedSql, "normalizedSql must not be null");
        Objects.requireNonNull(totalDuration, "totalDuration must not be null");
        Objects.requireNonNull(minDuration, "minDuration must not be null");
        Objects.requireNonNull(maxDuration, "maxDuration must not be null");
        Objects.requireNonNull(lastExecutedAt, "lastExecutedAt must not be null");
    }

    /**
     * Calculates the average execution duration across all runs.
     */
    public Duration avgDuration() {
        if (executionCount <= 0) return Duration.ZERO;
        return totalDuration.dividedBy(executionCount);
    }

    /**
     * Calculates the ratio of slow executions to total executions (0.0 to 1.0).
     */
    public double slowRatio() {
        if (executionCount <= 0) return 0.0;
        return (double) slowCount / executionCount;
    }
}
