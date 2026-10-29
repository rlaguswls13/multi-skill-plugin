package com.msp.holiday;

/**
 * Types of public holidays in South Korea.
 */
public enum HolidayType {
    /** Fixed solar holiday (e.g. 1/1, 3/1, 5/5, 6/6, 8/15, 10/3, 10/9, 12/25). */
    SOLAR,
    /** Lunar calendar holiday (e.g. Seollal 3-day, Buddha's Birthday, Chuseok 3-day). */
    LUNAR,
    /** Legally designated substitute holiday when public holiday overlaps with weekend/other holiday. */
    SUBSTITUTE,
    /** User-defined custom or temporary holiday (e.g. election days, special public holidays). */
    CUSTOM
}
