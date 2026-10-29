package com.msp.cron;

import java.time.DayOfWeek;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Thread-safe, high-performance cron expression parser and next trigger calculator.
 * Supports standard 5-part (Unix) and 6-part (Quartz with seconds) cron expressions.
 */
public final class CronExpression {

    private static final Map<String, Integer> MONTH_NAMES = Map.ofEntries(
            Map.entry("JAN", 1), Map.entry("FEB", 2), Map.entry("MAR", 3), Map.entry("APR", 4),
            Map.entry("MAY", 5), Map.entry("JUN", 6), Map.entry("JUL", 7), Map.entry("AUG", 8),
            Map.entry("SEP", 9), Map.entry("OCT", 10), Map.entry("NOV", 11), Map.entry("DEC", 12)
    );

    private static final Map<String, Integer> DAY_OF_WEEK_NAMES = Map.ofEntries(
            Map.entry("SUN", 0), Map.entry("MON", 1), Map.entry("TUE", 2), Map.entry("WED", 3),
            Map.entry("THU", 4), Map.entry("FRI", 5), Map.entry("SAT", 6)
    );

    private final String originalExpression;
    private final boolean[] seconds;
    private final boolean[] minutes;
    private final boolean[] hours;
    private final boolean[] daysOfMonth;
    private final boolean[] months;
    private final boolean[] daysOfWeek;
    private final boolean anyDayOfMonth;
    private final boolean anyDayOfWeek;

    private CronExpression(
            String originalExpression,
            boolean[] seconds,
            boolean[] minutes,
            boolean[] hours,
            boolean[] daysOfMonth,
            boolean[] months,
            boolean[] daysOfWeek,
            boolean anyDayOfMonth,
            boolean anyDayOfWeek
    ) {
        this.originalExpression = originalExpression;
        this.seconds = seconds;
        this.minutes = minutes;
        this.hours = hours;
        this.daysOfMonth = daysOfMonth;
        this.months = months;
        this.daysOfWeek = daysOfWeek;
        this.anyDayOfMonth = anyDayOfMonth;
        this.anyDayOfWeek = anyDayOfWeek;
    }

    /**
     * Parses a 5-part or 6-part cron expression string.
     */
    public static CronExpression parse(String expression) {
        Objects.requireNonNull(expression, "expression must not be null");
        String trimmed = expression.trim();
        String[] parts = trimmed.split("\\s+");

        if (parts.length != 5 && parts.length != 6) {
            throw new CronException("Invalid cron expression '" + expression + "': expected 5 or 6 fields, found " + parts.length);
        }

        int offset = 0;
        boolean[] seconds;
        if (parts.length == 6) {
            seconds = parseField(parts[0], 0, 59, Collections.emptyMap());
            offset = 1;
        } else {
            // 5-field cron implies 0 seconds
            seconds = new boolean[60];
            seconds[0] = true;
        }

        boolean[] minutes = parseField(parts[offset], 0, 59, Collections.emptyMap());
        boolean[] hours = parseField(parts[offset + 1], 0, 23, Collections.emptyMap());

        String domStr = parts[offset + 2];
        boolean anyDom = "?".equals(domStr) || "*".equals(domStr);
        boolean[] daysOfMonth = parseField(domStr, 1, 31, Collections.emptyMap());

        boolean[] months = parseField(parts[offset + 3], 1, 12, MONTH_NAMES);

        String dowStr = parts[offset + 4];
        boolean anyDow = "?".equals(dowStr) || "*".equals(dowStr);
        boolean[] daysOfWeek = parseField(dowStr, 0, 7, DAY_OF_WEEK_NAMES);

        // Normalize Sunday (0 and 7 are both Sunday)
        if (daysOfWeek[0]) {
            daysOfWeek[7] = true;
        }
        if (daysOfWeek[7]) {
            daysOfWeek[0] = true;
        }

        return new CronExpression(trimmed, seconds, minutes, hours, daysOfMonth, months, daysOfWeek, anyDom, anyDow);
    }

    private static boolean[] parseField(String rawField, int min, int max, Map<String, Integer> nameMap) {
        boolean[] bits = new boolean[max + 1];
        String field = rawField.toUpperCase(Locale.ROOT);

        // Replace textual names (JAN-DEC or SUN-SAT)
        for (Map.Entry<String, Integer> entry : nameMap.entrySet()) {
            field = field.replace(entry.getKey(), String.valueOf(entry.getValue()));
        }

        String[] subParts = field.split(",");
        for (String sub : subParts) {
            sub = sub.trim();
            if (sub.isEmpty()) {
                continue;
            }

            if ("*".equals(sub) || "?".equals(sub)) {
                Arrays.fill(bits, min, max + 1, true);
            } else if (sub.contains("/")) {
                String[] stepParts = sub.split("/");
                int step = Integer.parseInt(stepParts[1].trim());
                int start = min;
                int end = max;

                if (!"*".equals(stepParts[0].trim()) && !"?".equals(stepParts[0].trim())) {
                    if (stepParts[0].contains("-")) {
                        String[] range = stepParts[0].split("-");
                        start = Integer.parseInt(range[0].trim());
                        end = Integer.parseInt(range[1].trim());
                    } else {
                        start = Integer.parseInt(stepParts[0].trim());
                    }
                }

                validateRange(start, min, max);
                validateRange(end, min, max);
                for (int i = start; i <= end; i += step) {
                    bits[i] = true;
                }
            } else if (sub.contains("-")) {
                String[] range = sub.split("-");
                int start = Integer.parseInt(range[0].trim());
                int end = Integer.parseInt(range[1].trim());
                validateRange(start, min, max);
                validateRange(end, min, max);
                for (int i = start; i <= end; i++) {
                    bits[i] = true;
                }
            } else {
                int val = Integer.parseInt(sub);
                validateRange(val, min, max);
                bits[val] = true;
            }
        }
        return bits;
    }

    private static void validateRange(int val, int min, int max) {
        if (val < min || val > max) {
            throw new CronException("Value " + val + " out of valid range [" + min + ".." + max + "]");
        }
    }

    /**
     * Checks if the given instant in time matches this cron expression.
     */
    public boolean matches(ZonedDateTime time) {
        if (time == null) {
            return false;
        }

        if (!seconds[time.getSecond()]
                || !minutes[time.getMinute()]
                || !hours[time.getHour()]
                || !months[time.getMonthValue()]) {
            return false;
        }

        return matchesDay(time);
    }

    private boolean matchesDay(ZonedDateTime time) {
        if (anyDayOfMonth && anyDayOfWeek) {
            return true;
        }

        int dom = time.getDayOfMonth();
        DayOfWeek dow = time.getDayOfWeek();
        int dowVal = dow.getValue(); // 1 = Monday .. 7 = Sunday
        boolean dowMatches = daysOfWeek[dowVal] || (dowVal == 7 && daysOfWeek[0]);

        if (anyDayOfMonth) {
            return dowMatches;
        }
        if (anyDayOfWeek) {
            return daysOfMonth[dom];
        }
        // If both are specified, standard cron evaluates as OR
        return daysOfMonth[dom] || dowMatches;
    }

    /**
     * Calculates the next execution time strictly after the specified instant.
     */
    public ZonedDateTime next(ZonedDateTime from) {
        Objects.requireNonNull(from, "from must not be null");

        ZonedDateTime current = from.truncatedTo(ChronoUnit.SECONDS).plusSeconds(1);
        int maxIterations = 366 * 5 * 24 * 60; // 5 years upper limit

        for (int i = 0; i < maxIterations; i++) {
            // Month check
            if (!months[current.getMonthValue()]) {
                current = current.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).plusMonths(1);
                continue;
            }

            // Day check
            if (!matchesDay(current)) {
                current = current.withHour(0).withMinute(0).withSecond(0).plusDays(1);
                continue;
            }

            // Hour check
            if (!hours[current.getHour()]) {
                current = current.withMinute(0).withSecond(0).plusHours(1);
                continue;
            }

            // Minute check
            if (!minutes[current.getMinute()]) {
                current = current.withSecond(0).plusMinutes(1);
                continue;
            }

            // Second check
            if (!seconds[current.getSecond()]) {
                current = current.plusSeconds(1);
                continue;
            }

            return current;
        }

        throw new CronException("Unable to find next cron execution time within 5 years for expression: " + originalExpression);
    }

    /**
     * Calculates the next N executions strictly after the specified instant.
     */
    public List<ZonedDateTime> next(ZonedDateTime from, int count) {
        if (count <= 0) {
            return Collections.emptyList();
        }
        List<ZonedDateTime> result = new ArrayList<>(count);
        ZonedDateTime current = from;
        for (int i = 0; i < count; i++) {
            current = next(current);
            result.add(current);
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * Returns the raw cron expression string.
     */
    public String expression() {
        return originalExpression;
    }

    @Override
    public String toString() {
        return "CronExpression[" + originalExpression + "]";
    }
}
