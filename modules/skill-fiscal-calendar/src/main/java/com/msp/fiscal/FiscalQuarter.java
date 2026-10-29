package com.msp.fiscal;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Record representing a 3-month fiscal quarter (Q1, Q2, Q3, or Q4).
 *
 * @param fiscalYear    fiscal year numeric identifier (e.g. 2027)
 * @param quarterNumber quarter number (1, 2, 3, or 4)
 * @param startDate     first day of the quarter (inclusive)
 * @param endDate       last day of the quarter (inclusive)
 */
public record FiscalQuarter(
        int fiscalYear,
        int quarterNumber,
        LocalDate startDate,
        LocalDate endDate
) {
    public FiscalQuarter {
        Objects.requireNonNull(startDate, "startDate must not be null");
        Objects.requireNonNull(endDate, "endDate must not be null");
        if (quarterNumber < 1 || quarterNumber > 4) {
            throw new IllegalArgumentException("quarterNumber must be between 1 and 4, got: " + quarterNumber);
        }
    }

    /**
     * Standard formatted code, e.g. "FY2027-Q1".
     */
    public String code() {
        return "FY" + fiscalYear + "-Q" + quarterNumber;
    }

    /**
     * Checks if the given calendar date falls inside this fiscal quarter.
     */
    public boolean contains(LocalDate date) {
        Objects.requireNonNull(date, "date must not be null");
        return !date.isBefore(startDate) && !date.isAfter(endDate);
    }
}
