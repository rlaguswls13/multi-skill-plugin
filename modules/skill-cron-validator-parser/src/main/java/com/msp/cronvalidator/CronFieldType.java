package com.msp.cronvalidator;

import java.util.Locale;
import java.util.Map;

/**
 * Types of fields in a Cron expression.
 */
public enum CronFieldType {
    SECOND(0, 59, "second"),
    MINUTE(0, 59, "minute"),
    HOUR(0, 23, "hour"),
    DAY_OF_MONTH(1, 31, "day of month"),
    MONTH(1, 12, "month"),
    DAY_OF_WEEK(0, 7, "day of week");

    private final int minValue;
    private final int maxValue;
    private final String label;

    CronFieldType(int minValue, int maxValue, String label) {
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.label = label;
    }

    public int getMinValue() {
        return minValue;
    }

    public int getMaxValue() {
        return maxValue;
    }

    public String getLabel() {
        return label;
    }

    private static final Map<String, Integer> MONTH_NAMES = Map.ofEntries(
            Map.entry("JAN", 1), Map.entry("FEB", 2), Map.entry("MAR", 3),
            Map.entry("APR", 4), Map.entry("MAY", 5), Map.entry("JUN", 6),
            Map.entry("JUL", 7), Map.entry("AUG", 8), Map.entry("SEP", 9),
            Map.entry("OCT", 10), Map.entry("NOV", 11), Map.entry("DEC", 12)
    );

    private static final Map<String, Integer> DAY_NAMES = Map.ofEntries(
            Map.entry("SUN", 0), Map.entry("MON", 1), Map.entry("TUE", 2),
            Map.entry("WED", 3), Map.entry("THU", 4), Map.entry("FRI", 5),
            Map.entry("SAT", 6)
    );

    /**
     * Resolves an alias string (e.g. "JAN", "MON") or integer to an int value.
     */
    public int parseValue(String token) {
        String upper = token.trim().toUpperCase(Locale.ROOT);
        if (this == MONTH && MONTH_NAMES.containsKey(upper)) {
            return MONTH_NAMES.get(upper);
        }
        if (this == DAY_OF_WEEK && DAY_NAMES.containsKey(upper)) {
            return DAY_NAMES.get(upper);
        }
        return Integer.parseInt(upper);
    }
}
