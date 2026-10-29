package com.msp.cron;

import java.time.ZonedDateTime;
import java.util.List;

/**
 * Unified static facade for cron parsing, matching, next-run calculations, and scheduling.
 */
public final class Cron {

    private static final LightweightCronScheduler DEFAULT_SCHEDULER = LightweightCronScheduler.create();

    private Cron() {
    }

    /**
     * Parses a 5-part or 6-part cron expression.
     */
    public static CronExpression parse(String expression) {
        return CronExpression.parse(expression);
    }

    /**
     * Checks if a given time instant satisfies the cron expression.
     */
    public static boolean matches(String expression, ZonedDateTime time) {
        return parse(expression).matches(time);
    }

    /**
     * Calculates the next trigger time after the given timestamp.
     */
    public static ZonedDateTime next(String expression, ZonedDateTime from) {
        return parse(expression).next(from);
    }

    /**
     * Calculates the next N trigger times after the given timestamp.
     */
    public static List<ZonedDateTime> next(String expression, ZonedDateTime from, int count) {
        return parse(expression).next(from, count);
    }

    /**
     * Schedules a task using the default shared in-memory scheduler.
     */
    public static ScheduledTask schedule(String cronExpression, Runnable task) {
        return DEFAULT_SCHEDULER.schedule(cronExpression, task);
    }

    /**
     * Returns the shared default in-memory scheduler.
     */
    public static LightweightCronScheduler defaultScheduler() {
        return DEFAULT_SCHEDULER;
    }
}
