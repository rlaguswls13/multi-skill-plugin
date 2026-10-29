package com.msp.crontext;

import java.util.Objects;

/**
 * Parsed representation of a 5, 6, or 7-field cron expression.
 *
 * @param rawExpression original string
 * @param second        seconds field (null for 5-field cron)
 * @param minute        minutes field
 * @param hour          hours field
 * @param dayOfMonth    day of month field
 * @param month         month field
 * @param dayOfWeek     day of week field
 * @param year          optional year field (null if not specified)
 * @param fieldCount    total field count (5, 6, or 7)
 */
public record CronExpression(
        String rawExpression,
        String second,
        String minute,
        String hour,
        String dayOfMonth,
        String month,
        String dayOfWeek,
        String year,
        int fieldCount
) {
    public CronExpression {
        Objects.requireNonNull(rawExpression, "rawExpression must not be null");
        Objects.requireNonNull(minute, "minute must not be null");
        Objects.requireNonNull(hour, "hour must not be null");
        Objects.requireNonNull(dayOfMonth, "dayOfMonth must not be null");
        Objects.requireNonNull(month, "month must not be null");
        Objects.requireNonNull(dayOfWeek, "dayOfWeek must not be null");
    }

    /**
     * Parses a cron expression string into a {@link CronExpression}.
     * Supports 5-field (min hour dom month dow), 6-field (sec min hour dom month dow),
     * and 7-field (sec min hour dom month dow year) expressions.
     *
     * @param expression cron expression string
     * @return parsed CronExpression
     * @throws IllegalArgumentException if the expression is null, blank, or has invalid field count
     */
    public static CronExpression parse(String expression) {
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException("Cron expression cannot be null or blank");
        }

        String trimmed = expression.trim().replaceAll("\\s+", " ");
        String[] parts = trimmed.split(" ");

        return switch (parts.length) {
            case 5 -> new CronExpression(
                    trimmed,
                    null,
                    parts[0],
                    parts[1],
                    parts[2],
                    parts[3],
                    parts[4],
                    null,
                    5
            );
            case 6 -> new CronExpression(
                    trimmed,
                    parts[0],
                    parts[1],
                    parts[2],
                    parts[3],
                    parts[4],
                    parts[5],
                    null,
                    6
            );
            case 7 -> new CronExpression(
                    trimmed,
                    parts[0],
                    parts[1],
                    parts[2],
                    parts[3],
                    parts[4],
                    parts[5],
                    parts[6],
                    7
            );
            default -> throw new IllegalArgumentException(
                    "Cron expression must have 5, 6, or 7 fields, but got " + parts.length + ": '" + expression + "'"
            );
        };
    }

    /**
     * Checks if this expression explicitly specifies seconds.
     */
    public boolean hasSeconds() {
        return second != null;
    }

    /**
     * Checks if this expression explicitly specifies year.
     */
    public boolean hasYear() {
        return year != null && !year.equals("*") && !year.equals("?");
    }
}
