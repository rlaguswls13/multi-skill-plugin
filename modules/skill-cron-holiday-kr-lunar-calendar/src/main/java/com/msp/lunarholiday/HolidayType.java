package com.msp.lunarholiday;

/**
 * Category of Korean public holidays.
 */
public enum HolidayType {
    /** Fixed solar holiday (e.g. 1/1, 3/1, 5/5, 6/6, 8/15, 10/3, 10/9, 12/25) */
    SOLAR_FIXED,
    /** Lunar New Year 연휴 (Lunar 12/last day, 1/1, 1/2) */
    SEOLLAL,
    /** Buddha's Birthday (Lunar 4/8) */
    BUDDHAS_BIRTHDAY,
    /** Chuseok Harvest Festival 연휴 (Lunar 8/14, 8/15, 8/16) */
    CHUSEOK,
    /** Substitute public holiday (대체공휴일) */
    SUBSTITUTE
}
