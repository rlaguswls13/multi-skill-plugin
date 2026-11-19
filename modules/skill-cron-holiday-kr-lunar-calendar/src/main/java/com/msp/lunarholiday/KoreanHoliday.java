package com.msp.lunarholiday;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Represents a Korean public holiday.
 *
 * @param date         solar calendar date of the holiday
 * @param name         official Korean name (e.g. "설날", "추석", "광복절", "대체공휴일")
 * @param type         category of holiday
 * @param isSubstitute true if this is an officially designated substitute holiday (대체공휴일)
 */
public record KoreanHoliday(
        LocalDate date,
        String name,
        HolidayType type,
        boolean isSubstitute
) implements Comparable<KoreanHoliday> {

    public KoreanHoliday {
        Objects.requireNonNull(date, "date must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(type, "type must not be null");
    }

    public static KoreanHoliday of(LocalDate date, String name, HolidayType type) {
        return new KoreanHoliday(date, name, type, false);
    }

    public static KoreanHoliday substitute(LocalDate date, String originalHolidayName) {
        return new KoreanHoliday(date, "대체공휴일(" + originalHolidayName + ")", HolidayType.SUBSTITUTE, true);
    }

    @Override
    public int compareTo(KoreanHoliday o) {
        int cmp = this.date.compareTo(o.date);
        if (cmp != 0) return cmp;
        return this.name.compareTo(o.name);
    }
}
