package com.msp.cronbuilder;

import java.time.DayOfWeek;
import java.time.Month;
import java.util.Arrays;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Fluent, type-safe builder for standard 5-field (Unix/POSIX) and 6-field (Quartz/Spring)
 * Cron expressions.
 */
public final class CronBuilder {

    private static final Pattern VALID_FIELD_PATTERN = Pattern.compile("^[0-9a-zA-Z*,/?#L\\-]+$");

    private boolean includeSeconds = false;
    private String seconds = "0";
    private String minutes = "*";
    private String hours = "*";
    private String dayOfMonth = "*";
    private String month = "*";
    private String dayOfWeek = "*";

    public CronBuilder() {
    }

    /**
     * Creates a new blank CronBuilder instance.
     */
    public static CronBuilder create() {
        return new CronBuilder();
    }

    /**
     * Triggers every minute: (* * * * *).
     */
    public static CronBuilder everyMinute() {
        return new CronBuilder().minutes("*");
    }

    /**
     * Triggers every N minutes: (*&#47;interval * * * *).
     */
    public static CronBuilder everyMinutes(int interval) {
        if (interval <= 0 || interval > 59) {
            throw new IllegalArgumentException("Minute interval must be between 1 and 59: " + interval);
        }
        return new CronBuilder().minutes("*/" + interval);
    }

    /**
     * Triggers every hour at the specified minute: (minute * * * *).
     */
    public static CronBuilder everyHourAt(int minute) {
        validateRange("minute", minute, 0, 59);
        return new CronBuilder().minutes(String.valueOf(minute)).hours("*");
    }

    /**
     * Triggers daily at the specified hour and minute: (minute hour * * *).
     */
    public static CronBuilder dailyAt(int hour, int minute) {
        validateRange("hour", hour, 0, 23);
        validateRange("minute", minute, 0, 59);
        return new CronBuilder()
                .minutes(String.valueOf(minute))
                .hours(String.valueOf(hour))
                .dayOfMonth("*")
                .month("*")
                .dayOfWeek("*");
    }

    /**
     * Triggers weekly on a specified day of week, hour, and minute: (minute hour * * dayOfWeek).
     */
    public static CronBuilder weeklyOn(DayOfWeek day, int hour, int minute) {
        Objects.requireNonNull(day, "DayOfWeek cannot be null");
        validateRange("hour", hour, 0, 23);
        validateRange("minute", minute, 0, 59);
        return new CronBuilder()
                .minutes(String.valueOf(minute))
                .hours(String.valueOf(hour))
                .dayOfMonth("*")
                .month("*")
                .dayOfWeek(String.valueOf(day.getValue()));
    }

    /**
     * Triggers monthly on a specific day of month, hour, and minute: (minute hour dayOfMonth * *).
     */
    public static CronBuilder monthlyOn(int dayOfMonth, int hour, int minute) {
        validateRange("dayOfMonth", dayOfMonth, 1, 31);
        validateRange("hour", hour, 0, 23);
        validateRange("minute", minute, 0, 59);
        return new CronBuilder()
                .minutes(String.valueOf(minute))
                .hours(String.valueOf(hour))
                .dayOfMonth(String.valueOf(dayOfMonth))
                .month("*")
                .dayOfWeek("*");
    }

    /**
     * Enables 6-field cron mode with default seconds field "0".
     */
    public CronBuilder withSeconds() {
        this.includeSeconds = true;
        this.seconds = "0";
        return this;
    }

    /**
     * Enables 6-field cron mode with a specific second value (0-59).
     */
    public CronBuilder withSeconds(int second) {
        validateRange("second", second, 0, 59);
        this.includeSeconds = true;
        this.seconds = String.valueOf(second);
        return this;
    }

    /**
     * Enables 6-field cron mode with a custom second expression (e.g. "*", "0/5").
     */
    public CronBuilder withSeconds(String secondsExpression) {
        Objects.requireNonNull(secondsExpression, "secondsExpression cannot be null");
        this.includeSeconds = true;
        this.seconds = secondsExpression.trim();
        return this;
    }

    /**
     * Disables 6-field cron mode, reverting to standard 5-field cron.
     */
    public CronBuilder withoutSeconds() {
        this.includeSeconds = false;
        return this;
    }

    public CronBuilder minutes(String minutes) {
        this.minutes = Objects.requireNonNull(minutes, "minutes cannot be null").trim();
        return this;
    }

    public CronBuilder minutes(int minute) {
        validateRange("minute", minute, 0, 59);
        this.minutes = String.valueOf(minute);
        return this;
    }

    public CronBuilder hours(String hours) {
        this.hours = Objects.requireNonNull(hours, "hours cannot be null").trim();
        return this;
    }

    public CronBuilder hours(int hour) {
        validateRange("hour", hour, 0, 23);
        this.hours = String.valueOf(hour);
        return this;
    }

    public CronBuilder at(int hour, int minute) {
        return hours(hour).minutes(minute);
    }

    public CronBuilder betweenHours(int startHour, int endHour) {
        validateRange("startHour", startHour, 0, 23);
        validateRange("endHour", endHour, 0, 23);
        if (startHour >= endHour) {
            throw new IllegalArgumentException("startHour (" + startHour + ") must be less than endHour (" + endHour + ")");
        }
        this.hours = startHour + "-" + endHour;
        return this;
    }

    public CronBuilder dayOfMonth(String dayOfMonth) {
        this.dayOfMonth = Objects.requireNonNull(dayOfMonth, "dayOfMonth cannot be null").trim();
        return this;
    }

    public CronBuilder dayOfMonth(int day) {
        validateRange("dayOfMonth", day, 1, 31);
        this.dayOfMonth = String.valueOf(day);
        return this;
    }

    public CronBuilder month(String month) {
        this.month = Objects.requireNonNull(month, "month cannot be null").trim();
        return this;
    }

    public CronBuilder month(int month) {
        validateRange("month", month, 1, 12);
        this.month = String.valueOf(month);
        return this;
    }

    public CronBuilder month(Month month) {
        Objects.requireNonNull(month, "Month cannot be null");
        this.month = String.valueOf(month.getValue());
        return this;
    }

    public CronBuilder dayOfWeek(String dayOfWeek) {
        this.dayOfWeek = Objects.requireNonNull(dayOfWeek, "dayOfWeek cannot be null").trim();
        return this;
    }

    public CronBuilder dayOfWeek(DayOfWeek day) {
        Objects.requireNonNull(day, "DayOfWeek cannot be null");
        this.dayOfWeek = String.valueOf(day.getValue());
        return this;
    }

    public CronBuilder daysOfWeek(DayOfWeek... days) {
        Objects.requireNonNull(days, "days cannot be null");
        if (days.length == 0) {
            throw new IllegalArgumentException("Must provide at least one day of week");
        }
        this.dayOfWeek = Arrays.stream(days)
                .map(d -> String.valueOf(d.getValue()))
                .collect(Collectors.joining(","));
        return this;
    }

    /**
     * Sets dayOfWeek to Monday through Friday (1-5).
     */
    public CronBuilder weekdays() {
        this.dayOfWeek = "1-5";
        return this;
    }

    /**
     * Sets dayOfWeek to Saturday and Sunday (6,7).
     */
    public CronBuilder weekends() {
        this.dayOfWeek = "6,7";
        return this;
    }

    /**
     * Builds and validates the Cron expression string.
     *
     * @return validated 5-field or 6-field Cron expression string
     * @throws IllegalStateException if fields fail syntax validation
     */
    public String build() {
        validateField("minutes", minutes);
        validateField("hours", hours);
        validateField("dayOfMonth", dayOfMonth);
        validateField("month", month);
        validateField("dayOfWeek", dayOfWeek);

        if (includeSeconds) {
            validateField("seconds", seconds);
            return String.join(" ", seconds, minutes, hours, dayOfMonth, month, dayOfWeek);
        } else {
            return String.join(" ", minutes, hours, dayOfMonth, month, dayOfWeek);
        }
    }

    private static void validateField(String fieldName, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Field '" + fieldName + "' cannot be null or empty");
        }
        if (!VALID_FIELD_PATTERN.matcher(value).matches()) {
            throw new IllegalStateException("Field '" + fieldName + "' has invalid characters: " + value);
        }
    }

    private static void validateRange(String name, int value, int min, int max) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(name + " must be between " + min + " and " + max + ", but was: " + value);
        }
    }

    @Override
    public String toString() {
        return build();
    }
}
