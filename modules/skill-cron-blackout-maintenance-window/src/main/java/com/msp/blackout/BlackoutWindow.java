package com.msp.blackout;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Sealed hierarchy representing maintenance blackout windows:
 * one-time, recurring daily, or recurring weekly (timezone-aware, overnight & DST-safe).
 */
public sealed interface BlackoutWindow
        permits BlackoutWindow.OneTimeWindow, BlackoutWindow.DailyWindow, BlackoutWindow.WeeklyWindow {

    String id();

    String description();

    /**
     * Returns {@code true} if {@code instant} falls inside this blackout window {@code [start, end)}.
     */
    default boolean contains(Instant instant) {
        return activeWindowEnd(instant).isPresent();
    }

    /**
     * If {@code instant} falls inside an active occurrence of this blackout window,
     * returns the exclusive end {@link Instant} of that occurrence; otherwise {@link Optional#empty()}.
     */
    Optional<Instant> activeWindowEnd(Instant instant);

    /**
     * A single fixed {@code [startInclusive, endExclusive)} blackout interval.
     */
    record OneTimeWindow(
            String id,
            String description,
            Instant startInclusive,
            Instant endExclusive
    ) implements BlackoutWindow {
        public OneTimeWindow {
            validateId(id);
            description = description == null ? "" : description;
            Objects.requireNonNull(startInclusive, "startInclusive must not be null");
            Objects.requireNonNull(endExclusive, "endExclusive must not be null");
            if (!startInclusive.isBefore(endExclusive)) {
                throw new IllegalArgumentException("startInclusive must be before endExclusive");
            }
        }

        @Override
        public Optional<Instant> activeWindowEnd(Instant instant) {
            Objects.requireNonNull(instant, "instant must not be null");
            if (!instant.isBefore(startInclusive) && instant.isBefore(endExclusive)) {
                return Optional.of(endExclusive);
            }
            return Optional.empty();
        }
    }

    /**
     * A recurring daily blackout window in a specific {@link ZoneId}.
     * Supports overnight windows where {@code startLocalTime > endLocalTime} (e.g., {@code 23:00} to {@code 02:00}).
     */
    record DailyWindow(
            String id,
            String description,
            LocalTime startLocalTime,
            LocalTime endLocalTime,
            ZoneId zoneId
    ) implements BlackoutWindow {
        public DailyWindow {
            validateId(id);
            description = description == null ? "" : description;
            Objects.requireNonNull(startLocalTime, "startLocalTime must not be null");
            Objects.requireNonNull(endLocalTime, "endLocalTime must not be null");
            Objects.requireNonNull(zoneId, "zoneId must not be null");
            if (startLocalTime.equals(endLocalTime)) {
                throw new IllegalArgumentException("startLocalTime and endLocalTime must differ");
            }
        }

        @Override
        public Optional<Instant> activeWindowEnd(Instant instant) {
            Objects.requireNonNull(instant, "instant must not be null");
            return findRecurringWindowEnd(instant, null, startLocalTime, endLocalTime, zoneId);
        }
    }

    /**
     * A recurring weekly blackout window on selected {@link DayOfWeek}s in a specific {@link ZoneId}.
     * Supports overnight wrap-around across midnight into the next day.
     */
    record WeeklyWindow(
            String id,
            String description,
            Set<DayOfWeek> daysOfWeek,
            LocalTime startLocalTime,
            LocalTime endLocalTime,
            ZoneId zoneId
    ) implements BlackoutWindow {
        public WeeklyWindow {
            validateId(id);
            description = description == null ? "" : description;
            Objects.requireNonNull(daysOfWeek, "daysOfWeek must not be null");
            if (daysOfWeek.isEmpty()) {
                throw new IllegalArgumentException("daysOfWeek must not be empty");
            }
            daysOfWeek = Set.copyOf(EnumSet.copyOf(daysOfWeek));
            Objects.requireNonNull(startLocalTime, "startLocalTime must not be null");
            Objects.requireNonNull(endLocalTime, "endLocalTime must not be null");
            Objects.requireNonNull(zoneId, "zoneId must not be null");
            if (startLocalTime.equals(endLocalTime)) {
                throw new IllegalArgumentException("startLocalTime and endLocalTime must differ");
            }
        }

        @Override
        public Optional<Instant> activeWindowEnd(Instant instant) {
            Objects.requireNonNull(instant, "instant must not be null");
            return findRecurringWindowEnd(instant, daysOfWeek, startLocalTime, endLocalTime, zoneId);
        }
    }

    private static void validateId(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("window id must not be null or blank");
        }
    }

    private static Optional<Instant> findRecurringWindowEnd(
            Instant instant,
            Set<DayOfWeek> allowedDays,
            LocalTime startTime,
            LocalTime endTime,
            ZoneId zoneId
    ) {
        LocalDate baseDate = instant.atZone(zoneId).toLocalDate();
        boolean overnight = startTime.isAfter(endTime);

        // Check yesterday, today, and tomorrow to handle overnight windows & DST shifts cleanly
        for (int delta = -1; delta <= 1; delta++) {
            LocalDate startDate = baseDate.plusDays(delta);
            if (allowedDays != null && !allowedDays.contains(startDate.getDayOfWeek())) {
                continue;
            }
            LocalDate endDate = overnight ? startDate.plusDays(1) : startDate;
            Instant windowStart = ZonedDateTime.of(startDate, startTime, zoneId).toInstant();
            Instant windowEnd = ZonedDateTime.of(endDate, endTime, zoneId).toInstant();
            if (windowStart.isBefore(windowEnd)
                    && !instant.isBefore(windowStart)
                    && instant.isBefore(windowEnd)) {
                return Optional.of(windowEnd);
            }
        }
        return Optional.empty();
    }
}
