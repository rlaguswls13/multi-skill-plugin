package com.msp.cronnext;

import java.time.DayOfWeek;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Objects;

/**
 * Pure Java 21 Cron Next-N execution calculator for standard 5-field cron expressions.
 * Supported fields: Minute (0-59), Hour (0-23), Day of Month (1-31), Month (1-12), Day of Week (0-7, 0/7=Sun).
 * Supports: '*', ',', '-', '/'
 */
public final class CronNextCalculator {

    private final BitSet minutes = new BitSet(60);
    private final BitSet hours = new BitSet(24);
    private final BitSet daysOfMonth = new BitSet(32);
    private final BitSet months = new BitSet(13);
    private final BitSet daysOfWeek = new BitSet(8);

    private final String expression;

    private CronNextCalculator(String expression) {
        this.expression = Objects.requireNonNull(expression, "Cron expression must not be null").trim();
        parse(this.expression);
    }

    public static CronNextCalculator of(String expression) {
        return new CronNextCalculator(expression);
    }

    public static ZonedDateTime next(String expression, ZonedDateTime from) {
        return of(expression).next(from);
    }

    public static List<ZonedDateTime> nextN(String expression, ZonedDateTime from, int count) {
        return of(expression).nextN(from, count);
    }

    public ZonedDateTime next(ZonedDateTime from) {
        Objects.requireNonNull(from, "from time must not be null");
        // Start from next minute, truncated to minutes
        ZonedDateTime current = from.truncatedTo(ChronoUnit.MINUTES).plusMinutes(1);
        ZonedDateTime limit = current.plusYears(5); // Guard against infinite loop

        while (current.isBefore(limit)) {
            int month = current.getMonthValue();
            if (!months.get(month)) {
                current = current.plusMonths(1).withDayOfMonth(1).withHour(0).withMinute(0);
                continue;
            }

            int dayOfMonth = current.getDayOfMonth();
            int dayOfWeek = current.getDayOfWeek().getValue() % 7; // Sunday: 7 % 7 = 0
            if (!daysOfMonth.get(dayOfMonth) || !daysOfWeek.get(dayOfWeek)) {
                current = current.plusDays(1).withHour(0).withMinute(0);
                continue;
            }

            int hour = current.getHour();
            if (!hours.get(hour)) {
                current = current.plusHours(1).withMinute(0);
                continue;
            }

            int minute = current.getMinute();
            if (!minutes.get(minute)) {
                current = current.plusMinutes(1);
                continue;
            }

            return current;
        }

        throw new IllegalStateException("No execution found within 5 years for cron: " + expression);
    }

    public List<ZonedDateTime> nextN(ZonedDateTime from, int count) {
        if (count <= 0) {
            return List.of();
        }
        List<ZonedDateTime> list = new ArrayList<>(count);
        ZonedDateTime current = from;
        for (int i = 0; i < count; i++) {
            current = next(current);
            list.add(current);
        }
        return list;
    }

    public String getExpression() {
        return expression;
    }

    private void parse(String expr) {
        String[] parts = expr.split("\\s+");
        if (parts.length != 5) {
            throw new IllegalArgumentException("Cron expression must consist of exactly 5 fields (minute hour dom month dow): " + expr);
        }

        parseField(parts[0], minutes, 0, 59);
        parseField(parts[1], hours, 0, 23);
        parseField(parts[2], daysOfMonth, 1, 31);
        parseField(parts[3], months, 1, 12);
        parseField(parts[4], daysOfWeek, 0, 7);

        // Map 7 (Sunday) to 0 (Sunday) as well
        if (daysOfWeek.get(7)) {
            daysOfWeek.set(0);
        }
    }

    private static void parseField(String part, BitSet target, int min, int max) {
        String[] items = part.split(",");
        for (String item : items) {
            if (item.contains("/")) {
                String[] sub = item.split("/");
                int step = Integer.parseInt(sub[1]);
                if (step <= 0) throw new IllegalArgumentException("Step must be > 0: " + item);
                int start = min;
                int end = max;
                if (!sub[0].equals("*")) {
                    if (sub[0].contains("-")) {
                        String[] range = sub[0].split("-");
                        start = Integer.parseInt(range[0]);
                        end = Integer.parseInt(range[1]);
                    } else {
                        start = Integer.parseInt(sub[0]);
                    }
                }
                for (int val = start; val <= end; val += step) {
                    target.set(val);
                }
            } else if (item.contains("-")) {
                String[] range = item.split("-");
                int start = Integer.parseInt(range[0]);
                int end = Integer.parseInt(range[1]);
                if (start > end) throw new IllegalArgumentException("Invalid range: " + item);
                for (int val = start; val <= end; val++) {
                    target.set(val);
                }
            } else if (item.equals("*")) {
                for (int val = min; val <= max; val++) {
                    target.set(val);
                }
            } else {
                int val = Integer.parseInt(item);
                if (val < min || val > max) {
                    throw new IllegalArgumentException("Value out of range [" + min + "-" + max + "]: " + val);
                }
                target.set(val);
            }
        }
    }
}
