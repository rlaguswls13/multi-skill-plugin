package com.msp.fiscal;

/**
 * Strategy for naming a fiscal year when it spans across two calendar years.
 */
public enum YearNamingConvention {
    /**
     * Fiscal year is named after the calendar year in which it ends.
     * <p>
     * Standard US Federal convention: October 2026 to September 2027 is <b>FY2027</b>.
     */
    END_YEAR,

    /**
     * Fiscal year is named after the calendar year in which it begins.
     * <p>
     * Convention used in some jurisdictions: October 2026 to September 2027 is <b>FY2026</b>.
     */
    START_YEAR
}
