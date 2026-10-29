package com.msp.solarlunar;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;

/**
 * Pure Java bidirectional Solar (Gregorian) to Lunar (East Asian Lunisolar) converter.
 * <p>
 * Uses a bit-packed compressed astronomical lookup table spanning 1900 to 2100 (201 years, &lt; 1KB memory).
 * Zero external dependencies.
 */
public final class SolarLunarConverter {

    /**
     * Earliest supported solar date (corresponds to Lunar 1900-01-01).
     */
    public static final LocalDate MIN_SOLAR_DATE = LocalDate.of(1900, 1, 31);

    /**
     * Latest supported solar date (end of year 2100).
     */
    public static final LocalDate MAX_SOLAR_DATE = LocalDate.of(2100, 12, 31);

    public static final int MIN_YEAR = 1900;
    public static final int MAX_YEAR = 2100;

    /**
     * Compressed 201-element astronomical lookup table (1900-2100).
     * Bits 0-3: Leap month index (0 if none, 1-12 if present).
     * Bits 4-15: 12 standard months length (1: 30 days, 0: 29 days).
     * Bit 16: Leap month length (1: 30 days, 0: 29 days).
     */
    private static final int[] LUNAR_INFO = {
            0x04bd8, 0x04ae0, 0x0a570, 0x054d5, 0x0d260, 0x0d950, 0x16554, 0x056a0, 0x09ad0, 0x055d2, // 1900-1909
            0x04ae0, 0x0a5b6, 0x0a4d0, 0x0d250, 0x1d255, 0x0b540, 0x0d6a0, 0x0ada2, 0x095b0, 0x14977, // 1910-1919
            0x04970, 0x0a4b0, 0x0b4b5, 0x06a50, 0x06d40, 0x1ab54, 0x02b60, 0x09570, 0x052f2, 0x04970, // 1920-1929
            0x06566, 0x0d4a0, 0x0ea50, 0x06e95, 0x05ad0, 0x02b60, 0x186e3, 0x092e0, 0x1c8d7, 0x0c950, // 1930-1939
            0x0d4a0, 0x1d8a6, 0x0b550, 0x056a0, 0x1a5b4, 0x025d0, 0x092d0, 0x0d2b2, 0x0a950, 0x0b557, // 1940-1949
            0x06ca0, 0x0b550, 0x15355, 0x04da0, 0x0a5b0, 0x14573, 0x052b0, 0x0a9a8, 0x0e950, 0x06aa0, // 1950-1959
            0x0aea6, 0x0ab50, 0x04b60, 0x0aae4, 0x0a570, 0x05260, 0x0f263, 0x0d950, 0x05b57, 0x056a0, // 1960-1969
            0x096d0, 0x04dd5, 0x04ad0, 0x0a4d0, 0x0d4d4, 0x0d250, 0x0d558, 0x0b540, 0x0b6a0, 0x195a6, // 1970-1979
            0x095b0, 0x049b0, 0x0a974, 0x0a4b0, 0x0b27a, 0x06a50, 0x06d40, 0x0af46, 0x0ab60, 0x09570, // 1980-1989
            0x04af5, 0x04970, 0x064b0, 0x074a3, 0x0ea50, 0x06b58, 0x055c0, 0x0ab60, 0x096d5, 0x092e0, // 1990-1999
            0x0c960, 0x0d954, 0x0d4a0, 0x0da50, 0x07552, 0x056a0, 0x0abb7, 0x025d0, 0x092d0, 0x0cab5, // 2000-2009
            0x0a950, 0x0b4a0, 0x0baa4, 0x0ad50, 0x055d9, 0x04ba0, 0x0a5b0, 0x15176, 0x052b0, 0x0a930, // 2010-2019
            0x07954, 0x06aa0, 0x0ad50, 0x05b52, 0x04b60, 0x0a6e6, 0x0a4e0, 0x0d260, 0x0ea65, 0x0d530, // 2020-2029
            0x05aa0, 0x076a3, 0x096d0, 0x04afb, 0x04ad0, 0x0a4d0, 0x1d0b6, 0x0d250, 0x0d520, 0x0dd45, // 2030-2039
            0x0b5a0, 0x056d0, 0x055b2, 0x049b0, 0x0a577, 0x0a4b0, 0x0aa50, 0x1b255, 0x06d20, 0x0ada0, // 2040-2049
            0x14b63, 0x09370, 0x049f8, 0x04970, 0x064b0, 0x168a6, 0x0ea50, 0x06b20, 0x1a6c4, 0x0aae0, // 2050-2059
            0x0a2e0, 0x0d2e3, 0x0c960, 0x0d557, 0x0d4a0, 0x0da50, 0x05d55, 0x056a0, 0x0a6d0, 0x055d4, // 2060-2069
            0x052d0, 0x0a9b8, 0x0a950, 0x0b4a0, 0x0b6a6, 0x0ad50, 0x055a0, 0x0aba4, 0x0a5b0, 0x052b0, // 2070-2079
            0x0b273, 0x06930, 0x07337, 0x06aa0, 0x0ad50, 0x14b55, 0x04b60, 0x0a570, 0x054e4, 0x0d160, // 2080-2089
            0x0e968, 0x0d520, 0x0daa0, 0x16aa6, 0x056d0, 0x04ae0, 0x0a9d4, 0x0a2d0, 0x0d150, 0x0f252, // 2090-2099
            0x0d520                                                                                      // 2100
    };

    private SolarLunarConverter() {
    }

    // =========================================================================
    // Core Conversions
    // =========================================================================

    /**
     * Converts a Gregorian (solar) calendar date to its corresponding East Asian Lunisolar (lunar) date.
     *
     * @param solarDate solar date within 1900-01-31 to 2100-12-31
     * @return corresponding LunarDate record
     * @throws IllegalArgumentException if solarDate is outside supported range
     */
    public static LunarDate toLunar(LocalDate solarDate) {
        Objects.requireNonNull(solarDate, "solarDate must not be null");
        if (solarDate.isBefore(MIN_SOLAR_DATE) || solarDate.isAfter(MAX_SOLAR_DATE)) {
            throw new IllegalArgumentException(
                    "Solar date " + solarDate + " is out of supported range (" + MIN_SOLAR_DATE + " to " + MAX_SOLAR_DATE + ")"
            );
        }

        long offset = ChronoUnit.DAYS.between(MIN_SOLAR_DATE, solarDate);

        // 1. Locate lunar year
        int y = MIN_YEAR;
        while (y <= MAX_YEAR) {
            int yearDays = getLunarYearDays(y);
            if (offset < yearDays) {
                break;
            }
            offset -= yearDays;
            y++;
        }

        // 2. Locate lunar month and day within year y
        int leapMonth = getLeapMonth(y);
        for (int m = 1; m <= 12; m++) {
            int regularDays = getLunarMonthDays(y, m);
            if (offset < regularDays) {
                return new LunarDate(y, m, (int) offset + 1, false);
            }
            offset -= regularDays;

            // Check if this month has an intercalary leap month immediately following it
            if (m == leapMonth) {
                int leapDays = getLeapMonthDays(y);
                if (offset < leapDays) {
                    return new LunarDate(y, m, (int) offset + 1, true);
                }
                offset -= leapDays;
            }
        }

        throw new IllegalStateException("Failed to calculate lunar date for: " + solarDate);
    }

    /**
     * Converts a Lunar date to its Gregorian (solar) calendar date.
     *
     * @param lunarDate LunarDate record
     * @return Optional containing the LocalDate, or empty if date is invalid in the lunar calendar
     */
    public static Optional<LocalDate> toSolar(LunarDate lunarDate) {
        Objects.requireNonNull(lunarDate, "lunarDate must not be null");
        return toSolar(lunarDate.year(), lunarDate.month(), lunarDate.day(), lunarDate.isLeapMonth());
    }

    /**
     * Converts Lunar calendar components to its Gregorian (solar) calendar date.
     *
     * @param lunarYear    lunar year (1900-2100)
     * @param lunarMonth   lunar month (1-12)
     * @param lunarDay     lunar day (1-30)
     * @param isLeapMonth  true if requesting the leap month, false for regular month
     * @return Optional containing the LocalDate, or empty if parameters represent an invalid lunar date
     */
    public static Optional<LocalDate> toSolar(int lunarYear, int lunarMonth, int lunarDay, boolean isLeapMonth) {
        if (lunarYear < MIN_YEAR || lunarYear > MAX_YEAR) {
            return Optional.empty();
        }
        if (lunarMonth < 1 || lunarMonth > 12) {
            return Optional.empty();
        }
        if (lunarDay < 1 || lunarDay > 30) {
            return Optional.empty();
        }

        int leapMonth = getLeapMonth(lunarYear);
        if (isLeapMonth && leapMonth != lunarMonth) {
            // Requested leap month does not exist in this lunar year
            return Optional.empty();
        }

        int maxDays = isLeapMonth ? getLeapMonthDays(lunarYear) : getLunarMonthDays(lunarYear, lunarMonth);
        if (lunarDay > maxDays) {
            return Optional.empty();
        }

        // Calculate total day offset from MIN_SOLAR_DATE (Lunar 1900-01-01)
        long offset = 0;

        // Full preceding years
        for (int y = MIN_YEAR; y < lunarYear; y++) {
            offset += getLunarYearDays(y);
        }

        // Full preceding months in current year
        for (int m = 1; m < lunarMonth; m++) {
            offset += getLunarMonthDays(lunarYear, m);
            if (m == leapMonth) {
                offset += getLeapMonthDays(lunarYear);
            }
        }

        // If this is the leap month, add the regular month that came before it
        if (isLeapMonth) {
            offset += getLunarMonthDays(lunarYear, lunarMonth);
        }

        offset += (lunarDay - 1);

        LocalDate result = MIN_SOLAR_DATE.plusDays(offset);
        if (result.isAfter(MAX_SOLAR_DATE)) {
            return Optional.empty();
        }

        return Optional.of(result);
    }

    // =========================================================================
    // Astronomical Lookup Table Helpers
    // =========================================================================

    /**
     * Total number of days in the specified lunar year (typically 353-355 for regular years, 383-385 for leap years).
     */
    public static int getLunarYearDays(int year) {
        validateYear(year);
        int sum = 348;
        int info = LUNAR_INFO[year - MIN_YEAR];
        for (int mask = 0x8000; mask > 0x8; mask >>= 1) {
            if ((info & mask) != 0) {
                sum++;
            }
        }
        return sum + getLeapMonthDays(year);
    }

    /**
     * Returns the intercalary leap month (1-12) for the lunar year, or 0 if the year has no leap month.
     */
    public static int getLeapMonth(int year) {
        validateYear(year);
        return LUNAR_INFO[year - MIN_YEAR] & 0xf;
    }

    /**
     * Returns the number of days in the leap month for the lunar year (29 or 30), or 0 if no leap month.
     */
    public static int getLeapMonthDays(int year) {
        validateYear(year);
        if (getLeapMonth(year) == 0) {
            return 0;
        }
        return (LUNAR_INFO[year - MIN_YEAR] & 0x10000) != 0 ? 30 : 29;
    }

    /**
     * Returns the number of days in a standard (non-leap) lunar month (29 or 30).
     */
    public static int getLunarMonthDays(int year, int month) {
        validateYear(year);
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("Lunar month must be 1-12, got: " + month);
        }
        return (LUNAR_INFO[year - MIN_YEAR] & (0x10000 >> month)) != 0 ? 30 : 29;
    }

    private static void validateYear(int year) {
        if (year < MIN_YEAR || year > MAX_YEAR) {
            throw new IllegalArgumentException("Lunar year " + year + " is out of range (" + MIN_YEAR + " to " + MAX_YEAR + ")");
        }
    }
}
