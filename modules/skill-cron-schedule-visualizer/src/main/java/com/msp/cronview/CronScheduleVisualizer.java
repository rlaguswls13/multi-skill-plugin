package com.msp.cronview;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 5-field Cron expression trigger calculator and ASCII timeline/heatmap schedule visualizer.
 * Completely zero-dependency and thread-safe.
 */
public final class CronScheduleVisualizer {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss (z)");
    private static final DateTimeFormatter DAY_FORMATTER = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH);

    private static final Map<String, Integer> MONTH_ALIASES = Map.ofEntries(
            Map.entry("JAN", 1), Map.entry("FEB", 2), Map.entry("MAR", 3),
            Map.entry("APR", 4), Map.entry("MAY", 5), Map.entry("JUN", 6),
            Map.entry("JUL", 7), Map.entry("AUG", 8), Map.entry("SEP", 9),
            Map.entry("OCT", 10), Map.entry("NOV", 11), Map.entry("DEC", 12)
    );

    private static final Map<String, Integer> DOW_ALIASES = Map.ofEntries(
            Map.entry("SUN", 0), Map.entry("MON", 1), Map.entry("TUE", 2),
            Map.entry("WED", 3), Map.entry("THU", 4), Map.entry("FRI", 5),
            Map.entry("SAT", 6)
    );

    private CronScheduleVisualizer() {
        // Utility class
    }

    /**
     * Calculates the next N trigger execution timestamps strictly after the given start time.
     *
     * @param cron     standard 5-part cron string (e.g. "0 9 * * 1-5")
     * @param fromTime start reference time (cannot be null)
     * @param count    number of upcoming triggers to return (must be > 0)
     * @return List of {@link ZonedDateTime} in chronological order
     */
    public static List<ZonedDateTime> getNextExecutions(String cron, ZonedDateTime fromTime, int count) {
        Objects.requireNonNull(cron, "cron expression cannot be null");
        Objects.requireNonNull(fromTime, "fromTime cannot be null");
        if (count <= 0) {
            throw new IllegalArgumentException("count must be greater than 0, got: " + count);
        }

        ParsedCron parsed = parse(cron);
        List<ZonedDateTime> triggers = new ArrayList<>(count);

        // Start checking from the next minute boundary (seconds and nanos truncated to 0)
        ZonedDateTime current = fromTime.truncatedTo(ChronoUnit.MINUTES).plusMinutes(1);
        ZonedDateTime limit = fromTime.plusYears(5); // Guard against infinite loop

        while (triggers.size() < count && current.isBefore(limit)) {
            // Check month
            int month = current.getMonthValue();
            if (!parsed.months.get(month)) {
                current = current.withDayOfMonth(1).withHour(0).withMinute(0).plusMonths(1);
                continue;
            }

            // Check day of month & day of week
            int dayOfMonth = current.getDayOfMonth();
            int dayOfWeek = current.getDayOfWeek().getValue() % 7; // Sunday: 7 % 7 = 0
            boolean domMatch = parsed.daysOfMonth.get(dayOfMonth);
            boolean dowMatch = parsed.daysOfWeek.get(dayOfWeek);

            boolean dayMatch;
            if (parsed.anyDayOfMonth && parsed.anyDayOfWeek) {
                dayMatch = true;
            } else if (parsed.anyDayOfMonth) {
                dayMatch = dowMatch;
            } else if (parsed.anyDayOfWeek) {
                dayMatch = domMatch;
            } else {
                // POSIX cron: when both DOM and DOW are specified, union (OR) applies
                dayMatch = domMatch || dowMatch;
            }

            if (!dayMatch) {
                current = current.withHour(0).withMinute(0).plusDays(1);
                continue;
            }

            // Check hour
            int hour = current.getHour();
            if (!parsed.hours.get(hour)) {
                current = current.withMinute(0).plusHours(1);
                continue;
            }

            // Check minute
            int minute = current.getMinute();
            if (parsed.minutes.get(minute)) {
                triggers.add(current);
            }

            current = current.plusMinutes(1);
        }

        return Collections.unmodifiableList(triggers);
    }

    /**
     * Renders an ASCII table displaying upcoming executions and intervals between triggers.
     */
    public static String renderTimelineTable(String cron, ZonedDateTime fromTime, int count) {
        List<ZonedDateTime> triggers = getNextExecutions(cron, fromTime, count);
        if (triggers.isEmpty()) {
            return "No upcoming triggers found for expression: " + cron;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Cron Schedule Timeline for: %s%n", cron));
        sb.append("+----+----------------------------------+-----+------------------------+\n");
        sb.append("| #  | Trigger Time                     | Day | Interval from Previous |\n");
        sb.append("+----+----------------------------------+-----+------------------------+\n");

        ZonedDateTime prev = null;
        for (int i = 0; i < triggers.size(); i++) {
            ZonedDateTime trigger = triggers.get(i);
            String day = trigger.format(DAY_FORMATTER).toUpperCase(Locale.ENGLISH);
            String timeStr = trigger.format(TIME_FORMATTER);
            String interval = (prev == null) ? "-" : formatDuration(Duration.between(prev, trigger));

            sb.append(String.format("| %-2d | %-32s | %-3s | %-22s |%n", i + 1, timeStr, day, interval));
            prev = trigger;
        }

        sb.append("+----+----------------------------------+-----+------------------------+");
        return sb.toString();
    }

    /**
     * Displays a 24-hour ASCII bar distribution of triggers per hour across the specified day window.
     */
    public static String renderDailyHeatmap(String cron, ZonedDateTime fromTime, int days) {
        Objects.requireNonNull(cron, "cron expression cannot be null");
        Objects.requireNonNull(fromTime, "fromTime cannot be null");
        if (days <= 0) {
            throw new IllegalArgumentException("days must be greater than 0, got: " + days);
        }

        ZonedDateTime end = fromTime.plusDays(days);
        // Estimate max executions for this window (up to 60 * 24 * days)
        int maxEstimate = Math.min(100_000, 60 * 24 * days);
        List<ZonedDateTime> allTriggers = new ArrayList<>();

        ZonedDateTime cursor = fromTime;
        while (cursor.isBefore(end)) {
            List<ZonedDateTime> nextBatch = getNextExecutions(cron, cursor, Math.min(1000, maxEstimate));
            if (nextBatch.isEmpty()) {
                break;
            }
            for (ZonedDateTime t : nextBatch) {
                if (t.isBefore(end)) {
                    allTriggers.add(t);
                } else {
                    break;
                }
            }
            cursor = nextBatch.getLast();
            if (allTriggers.size() >= maxEstimate || !cursor.isBefore(end)) {
                break;
            }
        }

        int[] hourlyCounts = new int[24];
        for (ZonedDateTime t : allTriggers) {
            hourlyCounts[t.getHour()]++;
        }

        int maxCount = Arrays.stream(hourlyCounts).max().orElse(0);

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("24-Hour Execution Heatmap (%d Days Window) for: %s (Total Triggers: %d)%n",
                days, cron, allTriggers.size()));
        sb.append("Hour | Triggers | Distribution\n");
        sb.append("-----+----------+--------------------------------------------------\n");

        for (int h = 0; h < 24; h++) {
            int count = hourlyCounts[h];
            int barLength = (maxCount == 0) ? 0 : (int) Math.round(((double) count / maxCount) * 30);
            String bar = "■".repeat(barLength);
            sb.append(String.format(" %02d  | %-8d | %s%n", h, count, bar));
        }

        return sb.toString();
    }

    private static String formatDuration(Duration duration) {
        long seconds = duration.getSeconds();
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0) {
            sb.append(days).append("d ");
        }
        if (hours > 0 || (days > 0 && minutes > 0)) {
            sb.append(hours).append("h ");
        }
        if (minutes > 0 || (days == 0 && hours == 0)) {
            sb.append(minutes).append("m");
        }
        return sb.toString().trim();
    }

    // =========================================================================
    // Cron Parsing Subsystem
    // =========================================================================

    private record ParsedCron(
            BitSet minutes,
            BitSet hours,
            BitSet daysOfMonth,
            BitSet months,
            BitSet daysOfWeek,
            boolean anyDayOfMonth,
            boolean anyDayOfWeek
    ) {}

    private static ParsedCron parse(String cronExpr) {
        String[] parts = cronExpr.trim().split("\\s+");
        if (parts.length != 5) {
            throw new IllegalArgumentException(
                    "Standard cron expression must contain exactly 5 fields (minute hour DOM month DOW), got: " + parts.length
            );
        }

        BitSet minutes = parseField(parts[0], 0, 59, null);
        BitSet hours = parseField(parts[1], 0, 23, null);
        boolean anyDom = parts[2].equals("*") || parts[2].equals("?");
        BitSet daysOfMonth = parseField(parts[2], 1, 31, null);
        BitSet months = parseField(parts[3], 1, 12, MONTH_ALIASES);
        boolean anyDow = parts[4].equals("*") || parts[4].equals("?");
        BitSet daysOfWeek = parseDowField(parts[4]);

        return new ParsedCron(minutes, hours, daysOfMonth, months, daysOfWeek, anyDom, anyDow);
    }

    private static BitSet parseField(String field, int min, int max, Map<String, Integer> aliases) {
        BitSet bitSet = new BitSet(max + 1);
        String[] tokens = field.split(",");

        for (String token : tokens) {
            token = token.trim();
            if (token.isEmpty()) {
                continue;
            }

            int step = 1;
            int slashIdx = token.indexOf('/');
            String rangePart = token;
            if (slashIdx != -1) {
                rangePart = token.substring(0, slashIdx);
                step = Integer.parseInt(token.substring(slashIdx + 1));
                if (step <= 0) {
                    throw new IllegalArgumentException("Step must be positive in: " + token);
                }
            }

            int start;
            int end;
            if (rangePart.equals("*") || rangePart.equals("?")) {
                start = min;
                end = max;
            } else if (rangePart.contains("-")) {
                String[] rangeTokens = rangePart.split("-");
                start = resolveValue(rangeTokens[0], aliases);
                end = resolveValue(rangeTokens[1], aliases);
            } else {
                start = resolveValue(rangePart, aliases);
                end = (slashIdx != -1) ? max : start;
            }

            if (start < min || end > max || start > end) {
                throw new IllegalArgumentException(String.format("Invalid range [%d-%d] for field range [%d-%d]", start, end, min, max));
            }

            for (int val = start; val <= end; val += step) {
                bitSet.set(val);
            }
        }

        return bitSet;
    }

    private static BitSet parseDowField(String field) {
        BitSet raw = parseField(field, 0, 7, DOW_ALIASES);
        BitSet normalized = new BitSet(7); // 0 = Sun, 1 = Mon ... 6 = Sat
        for (int i = 0; i <= 6; i++) {
            if (raw.get(i)) {
                normalized.set(i);
            }
        }
        if (raw.get(7)) { // 7 is also Sunday in standard cron
            normalized.set(0);
        }
        return normalized;
    }

    private static int resolveValue(String token, Map<String, Integer> aliases) {
        String upper = token.trim().toUpperCase(Locale.ENGLISH);
        if (aliases != null && aliases.containsKey(upper)) {
            return aliases.get(upper);
        }
        return Integer.parseInt(upper);
    }
}
