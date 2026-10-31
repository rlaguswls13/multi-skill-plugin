package com.msp.cronmerge;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Utility for normalizing, expanding, and merging scheduled job execution intervals.
 */
public final class CronIntervalMerger {

    private CronIntervalMerger() {
    }

    /**
     * Merges any overlapping or contiguous intervals into non-overlapping, sorted intervals.
     *
     * @param intervals list of time intervals
     * @return unmodifiable list of merged, sorted, non-overlapping intervals
     */
    public static List<TimeInterval> mergeIntervals(List<TimeInterval> intervals) {
        if (intervals == null || intervals.isEmpty()) {
            return List.of();
        }

        List<TimeInterval> sorted = new ArrayList<>(intervals);
        Collections.sort(sorted);

        List<TimeInterval> merged = new ArrayList<>();
        TimeInterval current = sorted.get(0);

        for (int i = 1; i < sorted.size(); i++) {
            TimeInterval next = sorted.get(i);
            // Overlapping or contiguous: current.end >= next.start
            if (!next.start().isAfter(current.end())) {
                Instant maxEnd = current.end().isAfter(next.end()) ? current.end() : next.end();
                current = new TimeInterval(current.start(), maxEnd);
            } else {
                merged.add(current);
                current = next;
            }
        }
        merged.add(current);

        return Collections.unmodifiableList(merged);
    }

    /**
     * Expands simplified 5-field cron schedules within [windowStart, windowEnd) for all jobs in UTC
     * and returns the merged, non-overlapping execution intervals.
     */
    public static List<TimeInterval> calculateMergedExecutionWindows(
            List<ScheduledJob> jobs,
            Instant windowStart,
            Instant windowEnd
    ) {
        return calculateMergedExecutionWindows(jobs, windowStart, windowEnd, ZoneOffset.UTC);
    }

    /**
     * Expands simplified 5-field cron schedules within [windowStart, windowEnd) in the specified time zone
     * and returns the merged, non-overlapping execution intervals.
     */
    public static List<TimeInterval> calculateMergedExecutionWindows(
            List<ScheduledJob> jobs,
            Instant windowStart,
            Instant windowEnd,
            ZoneId zoneId
    ) {
        Objects.requireNonNull(jobs, "Jobs list cannot be null");
        Objects.requireNonNull(windowStart, "Window start cannot be null");
        Objects.requireNonNull(windowEnd, "Window end cannot be null");
        Objects.requireNonNull(zoneId, "ZoneId cannot be null");

        if (windowStart.isAfter(windowEnd)) {
            throw new IllegalArgumentException("Window start " + windowStart + " cannot be after window end " + windowEnd);
        }
        if (jobs.isEmpty() || windowStart.equals(windowEnd)) {
            return List.of();
        }

        List<TimeInterval> rawIntervals = new ArrayList<>();

        for (ScheduledJob job : jobs) {
            CronSchedule schedule = new CronSchedule(job.cronExpression());
            ZonedDateTime current = windowStart.atZone(zoneId).truncatedTo(ChronoUnit.MINUTES);

            while (current.toInstant().isBefore(windowEnd)) {
                Instant currentInstant = current.toInstant();
                if (!currentInstant.isBefore(windowStart)) {
                    if (schedule.matches(current)) {
                        Instant jobEnd = currentInstant.plus(job.executionDuration());
                        rawIntervals.add(new TimeInterval(currentInstant, jobEnd));
                    }
                }
                current = current.plusMinutes(1);
            }
        }

        return mergeIntervals(rawIntervals);
    }

    /**
     * Lightweight 5-field standard cron matcher (minute, hour, day-of-month, month, day-of-week).
     */
    static final class CronSchedule {
        private final boolean[] minutes = new boolean[60];
        private final boolean[] hours = new boolean[24];
        private final boolean[] daysOfMonth = new boolean[32]; // 1-31
        private final boolean[] months = new boolean[13]; // 1-12
        private final boolean[] daysOfWeek = new boolean[8]; // 0-7 (0 & 7 = Sunday)

        CronSchedule(String expression) {
            String[] parts = expression.trim().split("\\s+");
            if (parts.length != 5) {
                throw new IllegalArgumentException("Expected 5-field cron expression, got: " + expression);
            }
            parseField(parts[0], 0, 59, minutes);
            parseField(parts[1], 0, 23, hours);
            parseField(parts[2], 1, 31, daysOfMonth);
            parseField(parts[3], 1, 12, months);
            parseField(parts[4], 0, 7, daysOfWeek);
            if (daysOfWeek[7]) {
                daysOfWeek[0] = true;
            }
            if (daysOfWeek[0]) {
                daysOfWeek[7] = true;
            }
        }

        boolean matches(ZonedDateTime zdt) {
            int m = zdt.getMinute();
            int h = zdt.getHour();
            int dom = zdt.getDayOfMonth();
            int mon = zdt.getMonthValue();
            int dow = zdt.getDayOfWeek().getValue() % 7; // Java DayOfWeek Mon=1..Sun=7 -> Sun%7=0

            return minutes[m] && hours[h] && daysOfMonth[dom] && months[mon] && daysOfWeek[dow];
        }

        private static void parseField(String part, int min, int max, boolean[] target) {
            if ("*".equals(part) || "?".equals(part)) {
                for (int i = min; i <= max; i++) {
                    target[i] = true;
                }
                return;
            }

            String[] items = part.split(",");
            for (String item : items) {
                if (item.contains("/")) {
                    String[] stepParts = item.split("/");
                    int step = Integer.parseInt(stepParts[1]);
                    int start = min;
                    int end = max;
                    if (!"*".equals(stepParts[0]) && !stepParts[0].isEmpty()) {
                        if (stepParts[0].contains("-")) {
                            String[] range = stepParts[0].split("-");
                            start = Integer.parseInt(range[0]);
                            end = Integer.parseInt(range[1]);
                        } else {
                            start = Integer.parseInt(stepParts[0]);
                        }
                    }
                    for (int i = start; i <= end; i += step) {
                        if (i >= min && i <= max) {
                            target[i] = true;
                        }
                    }
                } else if (item.contains("-")) {
                    String[] range = item.split("-");
                    int start = Integer.parseInt(range[0]);
                    int end = Integer.parseInt(range[1]);
                    for (int i = start; i <= end; i++) {
                        if (i >= min && i <= max) {
                            target[i] = true;
                        }
                    }
                } else {
                    int val = Integer.parseInt(item);
                    if (val >= min && val <= max) {
                        target[val] = true;
                    }
                }
            }
        }
    }
}
