package com.msp.bizday;

import java.time.LocalDate;

/**
 * Functional interface to determine whether a given calendar date is a non-working holiday.
 */
@FunctionalInterface
public interface HolidayPredicate {

    /**
     * Checks if the specified date is a holiday.
     *
     * @param date calendar date to evaluate
     * @return {@code true} if date is a holiday, {@code false} otherwise
     */
    boolean isHoliday(LocalDate date);
}
