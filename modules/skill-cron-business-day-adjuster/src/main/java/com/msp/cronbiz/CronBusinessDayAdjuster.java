package com.msp.cronbiz;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Business Day schedule adjuster for Cron executions rolling over weekends and public/custom holidays.
 * <p>
 * Zero 3rd-party runtime dependencies. Pure Java 21 standard date/time library.
 * Thread-safe and immutable.
 */
public final class CronBusinessDayAdjuster {

    private final Set<LocalDate> customHolidays;

    /**
     * Constructs an adjuster with a specific set of holidays.
     *
     * @param customHolidays set of holiday dates where business executions should not occur
     */
    public CronBusinessDayAdjuster(Set<LocalDate> customHolidays) {
        if (customHolidays == null || customHolidays.isEmpty()) {
            this.customHolidays = Collections.emptySet();
        } else {
            this.customHolidays = Collections.unmodifiableSet(new HashSet<>(customHolidays));
        }
    }

    /**
     * Constructs a default adjuster where Saturdays and Sundays are non-business days,
     * without additional custom holidays.
     */
    public CronBusinessDayAdjuster() {
        this(Collections.emptySet());
    }

    /**
     * Checks if the given date is a business day (Monday through Friday and not a custom holiday).
     *
     * @param date date to evaluate
     * @return {@code true} if business day; {@code false} if weekend or holiday
     */
    public boolean isBusinessDay(LocalDate date) {
        Objects.requireNonNull(date, "date must not be null");
        DayOfWeek dow = date.getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
            return false;
        }
        return !customHolidays.contains(date);
    }

    /**
     * Finds the next business day strictly after the specified date.
     *
     * @param date baseline date
     * @return next strictly later business day
     */
    public LocalDate nextBusinessDay(LocalDate date) {
        Objects.requireNonNull(date, "date must not be null");
        LocalDate cur = date.plusDays(1);
        while (!isBusinessDay(cur)) {
            cur = cur.plusDays(1);
        }
        return cur;
    }

    /**
     * Returns the date itself if it is already a business day, or the next business day if it is not.
     */
    public LocalDate nextOrSameBusinessDay(LocalDate date) {
        return isBusinessDay(date) ? date : nextBusinessDay(date);
    }

    /**
     * Finds the previous business day strictly before the specified date.
     *
     * @param date baseline date
     * @return previous strictly earlier business day
     */
    public LocalDate previousBusinessDay(LocalDate date) {
        Objects.requireNonNull(date, "date must not be null");
        LocalDate cur = date.minusDays(1);
        while (!isBusinessDay(cur)) {
            cur = cur.minusDays(1);
        }
        return cur;
    }

    /**
     * Returns the date itself if it is already a business day, or the previous business day if it is not.
     */
    public LocalDate previousOrSameBusinessDay(LocalDate date) {
        return isBusinessDay(date) ? date : previousBusinessDay(date);
    }

    /**
     * Adjusts a scheduled {@link ZonedDateTime} according to the specified {@link BusinessDayRollPolicy}.
     * If the scheduled time falls on a business day, it is returned unchanged.
     *
     * @param scheduledTime original cron execution timestamp
     * @param policy        roll policy
     * @return an {@link Optional} containing the adjusted timestamp with preserved time/timezone,
     *         or {@link Optional#empty()} if the policy is {@link BusinessDayRollPolicy#SKIP}.
     */
    public Optional<ZonedDateTime> adjust(ZonedDateTime scheduledTime, BusinessDayRollPolicy policy) {
        Objects.requireNonNull(scheduledTime, "scheduledTime must not be null");
        Objects.requireNonNull(policy, "policy must not be null");

        LocalDate date = scheduledTime.toLocalDate();
        if (isBusinessDay(date)) {
            return Optional.of(scheduledTime);
        }

        return switch (policy) {
            case SKIP -> Optional.empty();
            case ROLL_FORWARD -> Optional.of(scheduledTime.with(nextBusinessDay(date)));
            case ROLL_BACKWARD -> Optional.of(scheduledTime.with(previousBusinessDay(date)));
        };
    }

    /**
     * Adjusts a scheduled {@link LocalDateTime} according to the specified {@link BusinessDayRollPolicy}.
     *
     * @param scheduledTime original execution timestamp
     * @param policy        roll policy
     * @return adjusted timestamp or empty if skipped
     */
    public Optional<LocalDateTime> adjust(LocalDateTime scheduledTime, BusinessDayRollPolicy policy) {
        Objects.requireNonNull(scheduledTime, "scheduledTime must not be null");
        Objects.requireNonNull(policy, "policy must not be null");

        LocalDate date = scheduledTime.toLocalDate();
        if (isBusinessDay(date)) {
            return Optional.of(scheduledTime);
        }

        return switch (policy) {
            case SKIP -> Optional.empty();
            case ROLL_FORWARD -> Optional.of(scheduledTime.with(nextBusinessDay(date)));
            case ROLL_BACKWARD -> Optional.of(scheduledTime.with(previousBusinessDay(date)));
        };
    }

    /**
     * Returns the unmodifiable set of configured custom holidays.
     */
    public Set<LocalDate> holidays() {
        return customHolidays;
    }
}
