package com.msp.daterange;

import java.time.LocalDate;
import java.time.Period;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Represents the step amount used when advancing across a {@link DateRange}.
 *
 * @param period standard Java {@link Period}
 */
public record DateStep(Period period) {

    public DateStep {
        Objects.requireNonNull(period, "period must not be null");
        if (period.isZero() || period.isNegative()) {
            throw new IllegalArgumentException("DateStep period must be strictly positive, got: " + period);
        }
    }

    public static DateStep of(Period period) {
        return new DateStep(period);
    }

    public static DateStep ofDays(int days) {
        return new DateStep(Period.ofDays(days));
    }

    public static DateStep ofWeeks(int weeks) {
        return new DateStep(Period.ofWeeks(weeks));
    }

    public static DateStep ofMonths(int months) {
        return new DateStep(Period.ofMonths(months));
    }

    public static DateStep ofYears(int years) {
        return new DateStep(Period.ofYears(years));
    }

    public static DateStep of(int amount, ChronoUnit unit) {
        Objects.requireNonNull(unit, "unit must not be null");
        return switch (unit) {
            case DAYS -> ofDays(amount);
            case WEEKS -> ofWeeks(amount);
            case MONTHS -> ofMonths(amount);
            case YEARS -> ofYears(amount);
            default -> throw new IllegalArgumentException("Unsupported step ChronoUnit: " + unit + ". Use DAYS, WEEKS, MONTHS, or YEARS.");
        };
    }

    /**
     * Standard 1-day step.
     */
    public static DateStep oneDay() {
        return ofDays(1);
    }

    /**
     * Standard 1-week step.
     */
    public static DateStep oneWeek() {
        return ofWeeks(1);
    }

    /**
     * Standard 1-month step.
     */
    public static DateStep oneMonth() {
        return ofMonths(1);
    }

    /**
     * Advances the given date by this step.
     */
    public LocalDate next(LocalDate current) {
        return current.plus(period);
    }
}
