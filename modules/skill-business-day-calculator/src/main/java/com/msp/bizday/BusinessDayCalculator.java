package com.msp.bizday;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;

/**
 * High-performance, thread-safe business day (working day) calculator.
 * <p>
 * Supports:
 * <ul>
 *   <li>Configurable weekend days (defaults to Saturday and Sunday)</li>
 *   <li>Custom holiday predicates or sets of holiday dates</li>
 *   <li>Stepping forward / backward by business days</li>
 *   <li>Finding next / previous business days</li>
 *   <li>Counting business days in half-open or inclusive date ranges</li>
 * </ul>
 */
public final class BusinessDayCalculator {

    private static final Set<DayOfWeek> DEFAULT_WEEKEND = Set.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);
    private static final BusinessDayCalculator STANDARD_INSTANCE = new BusinessDayCalculator(DEFAULT_WEEKEND, d -> false);

    private final Set<DayOfWeek> weekendDays;
    private final HolidayPredicate holidayPredicate;

    /**
     * Constructs a calculator with the specified weekend days and holiday predicate.
     *
     * @param weekendDays      set of non-working days of the week (e.g., Saturday and Sunday)
     * @param holidayPredicate predicate returning true if a given date is a holiday
     */
    public BusinessDayCalculator(Set<DayOfWeek> weekendDays, HolidayPredicate holidayPredicate) {
        this.weekendDays = Collections.unmodifiableSet(
                Objects.requireNonNull(weekendDays, "weekendDays must not be null").isEmpty()
                        ? DEFAULT_WEEKEND
                        : EnumSet.copyOf(weekendDays)
        );
        this.holidayPredicate = Objects.requireNonNull(holidayPredicate, "holidayPredicate must not be null");
    }

    /**
     * Returns a standard calculator with Saturday and Sunday as weekends and no holidays.
     *
     * @return standard business day calculator
     */
    public static BusinessDayCalculator standard() {
        return STANDARD_INSTANCE;
    }

    /**
     * Creates a calculator with default weekends (Sat/Sun) and a set of holiday dates.
     *
     * @param holidays set of non-working holiday dates
     * @return BusinessDayCalculator
     */
    public static BusinessDayCalculator of(Set<LocalDate> holidays) {
        Objects.requireNonNull(holidays, "holidays must not be null");
        Set<LocalDate> copy = Set.copyOf(holidays);
        return new BusinessDayCalculator(DEFAULT_WEEKEND, copy::contains);
    }

    /**
     * Creates a calculator with default weekends (Sat/Sun) and a holiday predicate.
     *
     * @param predicate holiday predicate
     * @return BusinessDayCalculator
     */
    public static BusinessDayCalculator of(HolidayPredicate predicate) {
        return new BusinessDayCalculator(DEFAULT_WEEKEND, predicate);
    }

    /**
     * Creates a builder for flexible configuration.
     *
     * @return new Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Determines whether the given date is a business day (not a weekend and not a holiday).
     *
     * @param date calendar date to check
     * @return {@code true} if working business day, {@code false} otherwise
     */
    public boolean isBusinessDay(LocalDate date) {
        Objects.requireNonNull(date, "date must not be null");
        if (weekendDays.contains(date.getDayOfWeek())) {
            return false;
        }
        return !holidayPredicate.isHoliday(date);
    }

    /**
     * Returns the first business day strictly after the given date.
     *
     * @param date starting date
     * @return next business day
     */
    public LocalDate nextBusinessDay(LocalDate date) {
        Objects.requireNonNull(date, "date must not be null");
        LocalDate current = date.plusDays(1);
        while (!isBusinessDay(current)) {
            current = current.plusDays(1);
        }
        return current;
    }

    /**
     * Returns the first business day strictly before the given date.
     *
     * @param date starting date
     * @return previous business day
     */
    public LocalDate previousBusinessDay(LocalDate date) {
        Objects.requireNonNull(date, "date must not be null");
        LocalDate current = date.minusDays(1);
        while (!isBusinessDay(current)) {
            current = current.minusDays(1);
        }
        return current;
    }

    /**
     * Adds the specified number of business days to the given date.
     * <ul>
     *   <li>If {@code days > 0}: advances forward by {@code days} business days.</li>
     *   <li>If {@code days < 0}: moves backward by {@code |days|} business days.</li>
     *   <li>If {@code days == 0}: returns {@code date} if it is a business day,
     *       otherwise advances to the next business day.</li>
     * </ul>
     *
     * @param date starting date
     * @param days number of business days to add (can be positive, negative, or zero)
     * @return adjusted date
     */
    public LocalDate addBusinessDays(LocalDate date, int days) {
        Objects.requireNonNull(date, "date must not be null");
        if (days == 0) {
            return isBusinessDay(date) ? date : nextBusinessDay(date);
        }

        LocalDate current = date;
        if (days > 0) {
            int remaining = days;
            while (remaining > 0) {
                current = current.plusDays(1);
                if (isBusinessDay(current)) {
                    remaining--;
                }
            }
        } else {
            int remaining = -days;
            while (remaining > 0) {
                current = current.minusDays(1);
                if (isBusinessDay(current)) {
                    remaining--;
                }
            }
        }
        return current;
    }

    /**
     * Counts business days in the half-open interval {@code [start, end)}.
     * <p>
     * If {@code start.isEqual(end)}, returns {@code 0}.
     * If {@code start.isAfter(end)}, returns {@code -countBusinessDays(end, start)}.
     *
     * @param start start date (inclusive)
     * @param end   end date (exclusive)
     * @return number of business days
     */
    public long countBusinessDays(LocalDate start, LocalDate end) {
        Objects.requireNonNull(start, "start must not be null");
        Objects.requireNonNull(end, "end must not be null");

        if (start.isEqual(end)) {
            return 0;
        }
        if (start.isAfter(end)) {
            return -countBusinessDays(end, start);
        }

        long count = 0;
        LocalDate current = start;
        while (current.isBefore(end)) {
            if (isBusinessDay(current)) {
                count++;
            }
            current = current.plusDays(1);
        }
        return count;
    }

    /**
     * Counts business days in the fully closed interval {@code [start, end]} inclusive.
     *
     * @param start start date (inclusive)
     * @param end   end date (inclusive)
     * @return number of business days inclusive
     */
    public long countBusinessDaysInclusive(LocalDate start, LocalDate end) {
        Objects.requireNonNull(start, "start must not be null");
        Objects.requireNonNull(end, "end must not be null");

        if (start.isAfter(end)) {
            return -countBusinessDaysInclusive(end, start);
        }

        long count = 0;
        LocalDate current = start;
        while (!current.isAfter(end)) {
            if (isBusinessDay(current)) {
                count++;
            }
            current = current.plusDays(1);
        }
        return count;
    }

    public Set<DayOfWeek> getWeekendDays() {
        return weekendDays;
    }

    /**
     * Builder for {@link BusinessDayCalculator}.
     */
    public static final class Builder {
        private Set<DayOfWeek> weekendDays = DEFAULT_WEEKEND;
        private HolidayPredicate holidayPredicate = d -> false;

        public Builder weekend(Set<DayOfWeek> weekendDays) {
            this.weekendDays = Objects.requireNonNull(weekendDays, "weekendDays must not be null");
            return this;
        }

        public Builder holidays(Set<LocalDate> holidays) {
            Objects.requireNonNull(holidays, "holidays must not be null");
            Set<LocalDate> copy = Set.copyOf(holidays);
            this.holidayPredicate = copy::contains;
            return this;
        }

        public Builder holidayPredicate(HolidayPredicate predicate) {
            this.holidayPredicate = Objects.requireNonNull(predicate, "predicate must not be null");
            return this;
        }

        public BusinessDayCalculator build() {
            return new BusinessDayCalculator(weekendDays, holidayPredicate);
        }
    }
}
