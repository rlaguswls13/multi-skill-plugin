package com.msp.fiscal;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Record representing a 6-month fiscal half (H1 or H2).
 *
 * @param fiscalYear fiscal year numeric identifier
 * @param halfNumber half number (1 or 2)
 * @param startDate  first day of the half (inclusive)
 * @param endDate    last day of the half (inclusive)
 */
public record FiscalHalf(
        int fiscalYear,
        int halfNumber,
        LocalDate startDate,
        LocalDate endDate
) {
    public FiscalHalf {
        Objects.requireNonNull(startDate, "startDate must not be null");
        Objects.requireNonNull(endDate, "endDate must not be null");
        if (halfNumber < 1 || halfNumber > 2) {
            throw new IllegalArgumentException("halfNumber must be 1 or 2, got: " + halfNumber);
        }
    }

    /**
     * Standard formatted code, e.g. "FY2027-H1".
     */
    public String code() {
        return "FY" + fiscalYear + "-H" + halfNumber;
    }

    /**
     * Checks if the given calendar date falls inside this fiscal half.
     */
    public boolean contains(LocalDate date) {
        Objects.requireNonNull(date, "date must not be null");
        return !date.isBefore(startDate) && !date.isAfter(endDate);
    }
}
