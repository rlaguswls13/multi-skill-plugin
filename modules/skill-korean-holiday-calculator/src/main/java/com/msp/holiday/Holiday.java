package com.msp.holiday;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Represents a public or designated holiday in South Korea.
 *
 * @param name         holiday name in Korean (e.g., "삼일절", "어린이날", "대체공휴일 (삼일절)")
 * @param date         calendar date of the holiday
 * @param isSubstitute whether this holiday is a legal substitute holiday
 * @param type         classification of the holiday
 */
public record Holiday(
        String name,
        LocalDate date,
        boolean isSubstitute,
        HolidayType type
) implements Comparable<Holiday> {

    public Holiday {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(date, "date must not be null");
        if (type == null) {
            type = isSubstitute ? HolidayType.SUBSTITUTE : HolidayType.SOLAR;
        }
    }

    /**
     * Convenience constructor without explicit HolidayType.
     *
     * @param name         holiday name
     * @param date         holiday date
     * @param isSubstitute whether it is a substitute holiday
     */
    public Holiday(String name, LocalDate date, boolean isSubstitute) {
        this(name, date, isSubstitute, isSubstitute ? HolidayType.SUBSTITUTE : HolidayType.SOLAR);
    }

    @Override
    public int compareTo(Holiday other) {
        int cmp = this.date.compareTo(other.date);
        if (cmp != 0) {
            return cmp;
        }
        return this.name.compareTo(other.name);
    }
}
