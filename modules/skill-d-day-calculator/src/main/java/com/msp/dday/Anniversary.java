package com.msp.dday;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Record representing a named anniversary milestone.
 *
 * @param name   name or title of the milestone (e.g. "100일", "1주년")
 * @param date   calendar date of the milestone
 * @param number numeric milestone value (e.g. 100, 1)
 * @param isYearly true if milestone is annual (years), false if daily (days)
 */
public record Anniversary(
        String name,
        LocalDate date,
        int number,
        boolean isYearly
) {
    public Anniversary {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(date, "date must not be null");
    }
}
