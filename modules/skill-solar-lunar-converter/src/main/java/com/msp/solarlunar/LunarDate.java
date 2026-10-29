package com.msp.solarlunar;

/**
 * Record representing a date in the East Asian lunisolar (음력) calendar.
 *
 * @param year        lunar year (e.g. 2026)
 * @param month       lunar month (1 to 12)
 * @param day         lunar day (1 to 30)
 * @param isLeapMonth true if this is an intercalary leap month (윤달), false for a regular month (평달)
 */
public record LunarDate(
        int year,
        int month,
        int day,
        boolean isLeapMonth
) {
    public LunarDate {
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("Lunar month must be between 1 and 12, got: " + month);
        }
        if (day < 1 || day > 30) {
            throw new IllegalArgumentException("Lunar day must be between 1 and 30, got: " + day);
        }
    }

    /**
     * Returns a formatted Korean string, e.g. "음력 2026년 1월 1일" or "음력 2026년 윤2월 15일".
     */
    public String toKoreanString() {
        return "음력 " + year + "년 " + (isLeapMonth ? "윤" : "") + month + "월 " + day + "일";
    }

    @Override
    public String toString() {
        return String.format("%04d-%s%02d-%02d", year, (isLeapMonth ? "L" : ""), month, day);
    }
}
