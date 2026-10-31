package com.msp.crondiff;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.BitSet;
import java.util.Objects;

/**
 * Lightweight 5-field standard cron expression parser and evaluator.
 * Format: "minute hour day-of-month month day-of-week"
 */
public final class CronExpression {

    private final String rawExpression;
    private final BitSet minutes = new BitSet(60);
    private final BitSet hours = new BitSet(24);
    private final BitSet daysOfMonth = new BitSet(32);
    private final BitSet months = new BitSet(13);
    private final BitSet daysOfWeek = new BitSet(8);

    private final boolean domRestricted;
    private final boolean dowRestricted;

    private CronExpression(String expression) {
        this.rawExpression = Objects.requireNonNull(expression, "Cron expression must not be null").trim();
        String[] parts = this.rawExpression.split("\\s+");
        if (parts.length != 5) {
            throw new IllegalArgumentException("Standard cron expression must have exactly 5 fields, got " + parts.length + ": " + expression);
        }

        parseField(parts[0], 0, 59, minutes, false);
        parseField(parts[1], 0, 23, hours, false);
        parseField(parts[2], 1, 31, daysOfMonth, false);
        parseField(parts[3], 1, 12, months, false);
        parseField(parts[4], 0, 7, daysOfWeek, true);

        this.domRestricted = !parts[2].equals("*") && !parts[2].equals("?");
        this.dowRestricted = !parts[4].equals("*") && !parts[4].equals("?");
    }

    public static CronExpression parse(String expression) {
        return new CronExpression(expression);
    }

    public String getRawExpression() {
        return rawExpression;
    }

    /**
     * Checks if the given LocalDateTime satisfies this cron expression.
     */
    public boolean matches(LocalDateTime dt) {
        if (!minutes.get(dt.getMinute())) {
            return false;
        }
        if (!hours.get(dt.getHour())) {
            return false;
        }
        if (!months.get(dt.getMonthValue())) {
            return false;
        }

        int dom = dt.getDayOfMonth();
        DayOfWeek javaDow = dt.getDayOfWeek();
        int dow = javaDow == DayOfWeek.SUNDAY ? 0 : javaDow.getValue(); // 0 = Sunday, 1 = Monday ... 6 = Saturday

        boolean domMatch = daysOfMonth.get(dom);
        boolean dowMatch = daysOfWeek.get(dow) || (dow == 0 && daysOfWeek.get(7));

        if (domRestricted && dowRestricted) {
            return domMatch || dowMatch;
        } else if (domRestricted) {
            return domMatch;
        } else if (dowRestricted) {
            return dowMatch;
        }
        return true;
    }

    /**
     * Finds the next matching occurrence after the given reference time.
     * Searches up to 5 years into the future.
     */
    public LocalDateTime nextOccurrence(LocalDateTime after) {
        LocalDateTime cur = after.withSecond(0).withNano(0).plusMinutes(1);
        LocalDateTime limit = cur.plusYears(5);

        while (cur.isBefore(limit)) {
            if (matches(cur)) {
                return cur;
            }
            cur = cur.plusMinutes(1);
        }
        return null;
    }

    private static void parseField(String field, int min, int max, BitSet bitSet, boolean isDow) {
        String[] items = field.split(",");
        for (String item : items) {
            parseItem(item.trim(), min, max, bitSet, isDow);
        }
    }

    private static void parseItem(String item, int min, int max, BitSet bitSet, boolean isDow) {
        if (item.equals("*") || item.equals("?")) {
            bitSet.set(min, max + 1);
            return;
        }

        if (item.contains("/")) {
            String[] stepParts = item.split("/");
            if (stepParts.length != 2) {
                throw new IllegalArgumentException("Invalid step syntax: " + item);
            }
            int step = Integer.parseInt(stepParts[1]);
            if (step <= 0) {
                throw new IllegalArgumentException("Step must be positive: " + step);
            }

            int start = min;
            int end = max;
            if (!stepParts[0].equals("*") && !stepParts[0].equals("?")) {
                if (stepParts[0].contains("-")) {
                    String[] rangeParts = stepParts[0].split("-");
                    start = Integer.parseInt(rangeParts[0]);
                    end = Integer.parseInt(rangeParts[1]);
                } else {
                    start = Integer.parseInt(stepParts[0]);
                }
            }

            validateRange(start, end, min, max);
            for (int i = start; i <= end; i += step) {
                setBit(bitSet, i, isDow);
            }
            return;
        }

        if (item.contains("-")) {
            String[] rangeParts = item.split("-");
            if (rangeParts.length != 2) {
                throw new IllegalArgumentException("Invalid range syntax: " + item);
            }
            int start = Integer.parseInt(rangeParts[0]);
            int end = Integer.parseInt(rangeParts[1]);
            validateRange(start, end, min, max);
            for (int i = start; i <= end; i++) {
                setBit(bitSet, i, isDow);
            }
            return;
        }

        int singleVal = Integer.parseInt(item);
        if (singleVal < min || singleVal > max) {
            throw new IllegalArgumentException("Value out of bounds [" + min + ".." + max + "]: " + singleVal);
        }
        setBit(bitSet, singleVal, isDow);
    }

    private static void setBit(BitSet bitSet, int val, boolean isDow) {
        bitSet.set(val);
        if (isDow && val == 7) {
            bitSet.set(0);
        } else if (isDow && val == 0) {
            bitSet.set(7);
        }
    }

    private static void validateRange(int start, int end, int min, int max) {
        if (start < min || start > max || end < min || end > max || start > end) {
            throw new IllegalArgumentException("Invalid range [" + start + "-" + end + "] for bounds [" + min + ".." + max + "]");
        }
    }
}
