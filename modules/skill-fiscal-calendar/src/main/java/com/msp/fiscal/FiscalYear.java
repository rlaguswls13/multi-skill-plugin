package com.msp.fiscal;

import java.time.LocalDate;
import java.time.Month;
import java.util.Objects;

/**
 * Record representing a complete fiscal year period.
 *
 * @param year       fiscal year numeric identifier (e.g. 2027)
 * @param startDate  start date of the fiscal year (inclusive)
 * @param endDate    end date of the fiscal year (inclusive)
 * @param startMonth beginning calendar month of the fiscal year
 */
public record FiscalYear(
        int year,
        LocalDate startDate,
        LocalDate endDate,
        Month startMonth
) {
    public FiscalYear {
        Objects.requireNonNull(startDate, "startDate must not be null");
        Objects.requireNonNull(endDate, "endDate must not be null");
        Objects.requireNonNull(startMonth, "startMonth must not be null");
    }

    /**
     * Standard formatted code, e.g. "FY2027".
     */
    public String code() {
        return "FY" + year;
    }

    /**
     * Checks if the given calendar date falls inside this fiscal year.
     */
    public boolean contains(LocalDate date) {
        Objects.requireNonNull(date, "date must not be null");
        return !date.isBefore(startDate) && !date.isAfter(endDate);
    }
}
