package com.msp.daterange;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * An immutable, step-aware, policy-based date interval providing iteration and streaming
 * over {@link LocalDate} and {@link Instant}.
 *
 * @param start  start boundary date
 * @param end    end boundary date
 * @param policy interval boundary inclusion policy
 * @param step   increment step
 */
public record DateRange(
        LocalDate start,
        LocalDate end,
        InclusionPolicy policy,
        DateStep step
) implements Iterable<LocalDate> {

    public DateRange {
        Objects.requireNonNull(start, "start must not be null");
        Objects.requireNonNull(end, "end must not be null");
        Objects.requireNonNull(policy, "policy must not be null");
        Objects.requireNonNull(step, "step must not be null");
    }

    // =========================================================================
    // Factory Methods (LocalDate)
    // =========================================================================

    public static DateRange closed(LocalDate start, LocalDate end) {
        return new DateRange(start, end, InclusionPolicy.CLOSED, DateStep.oneDay());
    }

    public static DateRange halfOpen(LocalDate start, LocalDate end) {
        return new DateRange(start, end, InclusionPolicy.HALF_OPEN, DateStep.oneDay());
    }

    public static DateRange open(LocalDate start, LocalDate end) {
        return new DateRange(start, end, InclusionPolicy.OPEN, DateStep.oneDay());
    }

    public static DateRange of(LocalDate start, LocalDate end, InclusionPolicy policy) {
        return new DateRange(start, end, policy, DateStep.oneDay());
    }

    public static DateRange of(LocalDate start, LocalDate end, InclusionPolicy policy, DateStep step) {
        return new DateRange(start, end, policy, step);
    }

    // =========================================================================
    // Factory Methods (Instant with ZoneId)
    // =========================================================================

    public static DateRange closed(Instant start, Instant end, ZoneId zone) {
        Objects.requireNonNull(zone, "zone must not be null");
        return closed(start.atZone(zone).toLocalDate(), end.atZone(zone).toLocalDate());
    }

    public static DateRange halfOpen(Instant start, Instant end, ZoneId zone) {
        Objects.requireNonNull(zone, "zone must not be null");
        return halfOpen(start.atZone(zone).toLocalDate(), end.atZone(zone).toLocalDate());
    }

    public static DateRange between(Instant start, Instant end, ZoneId zone, InclusionPolicy policy) {
        Objects.requireNonNull(zone, "zone must not be null");
        return of(start.atZone(zone).toLocalDate(), end.atZone(zone).toLocalDate(), policy);
    }

    // =========================================================================
    // Step & Policy Modifiers
    // =========================================================================

    public DateRange withStep(DateStep newStep) {
        return new DateRange(this.start, this.end, this.policy, newStep);
    }

    public DateRange withStep(Period period) {
        return withStep(DateStep.of(period));
    }

    public DateRange withStep(int amount, ChronoUnit unit) {
        return withStep(DateStep.of(amount, unit));
    }

    public DateRange withPolicy(InclusionPolicy newPolicy) {
        return new DateRange(this.start, this.end, newPolicy, this.step);
    }

    // =========================================================================
    // Effective Boundaries & Queries
    // =========================================================================

    /**
     * The first calendar date that actually belongs to this range, or empty if range is empty.
     */
    public Optional<LocalDate> effectiveStart() {
        if (start.isAfter(end)) {
            return Optional.empty();
        }
        LocalDate s = policy.includesStart() ? start : start.plusDays(1);
        LocalDate e = policy.includesEnd() ? end : end.minusDays(1);
        if (s.isAfter(e)) {
            return Optional.empty();
        }
        return Optional.of(s);
    }

    /**
     * The last calendar date that actually belongs to this range, or empty if range is empty.
     */
    public Optional<LocalDate> effectiveEnd() {
        if (start.isAfter(end)) {
            return Optional.empty();
        }
        LocalDate s = policy.includesStart() ? start : start.plusDays(1);
        LocalDate e = policy.includesEnd() ? end : end.minusDays(1);
        if (s.isAfter(e)) {
            return Optional.empty();
        }
        return Optional.of(e);
    }

    /**
     * Checks if this range contains zero dates.
     */
    public boolean isEmpty() {
        return effectiveStart().isEmpty();
    }

    /**
     * Tests whether the specified date falls inside this date range according to its inclusion policy.
     */
    public boolean contains(LocalDate date) {
        Objects.requireNonNull(date, "date must not be null");
        if (isEmpty()) {
            return false;
        }

        boolean startOk = policy.includesStart() ? !date.isBefore(start) : date.isAfter(start);
        boolean endOk = policy.includesEnd() ? !date.isAfter(end) : date.isBefore(end);
        return startOk && endOk;
    }

    /**
     * Tests whether the specified instant falls inside this range at the given time zone.
     */
    public boolean contains(Instant instant, ZoneId zone) {
        Objects.requireNonNull(instant, "instant must not be null");
        Objects.requireNonNull(zone, "zone must not be null");
        return contains(instant.atZone(zone).toLocalDate());
    }

    /**
     * Checks if this date range overlaps with another date range.
     */
    public boolean overlaps(DateRange other) {
        Objects.requireNonNull(other, "other must not be null");
        Optional<LocalDate> thisS = this.effectiveStart();
        Optional<LocalDate> thisE = this.effectiveEnd();
        Optional<LocalDate> otherS = other.effectiveStart();
        Optional<LocalDate> otherE = other.effectiveEnd();

        if (thisS.isEmpty() || thisE.isEmpty() || otherS.isEmpty() || otherE.isEmpty()) {
            return false;
        }

        return !thisE.get().isBefore(otherS.get()) && !otherE.get().isBefore(thisS.get());
    }

    /**
     * Returns the intersecting sub-range of this range and another range, or {@link Optional#empty()} if disjoint.
     */
    public Optional<DateRange> intersection(DateRange other) {
        Objects.requireNonNull(other, "other must not be null");
        if (!overlaps(other)) {
            return Optional.empty();
        }

        LocalDate newStart = this.effectiveStart().get().isAfter(other.effectiveStart().get())
                ? this.effectiveStart().get()
                : other.effectiveStart().get();

        LocalDate newEnd = this.effectiveEnd().get().isBefore(other.effectiveEnd().get())
                ? this.effectiveEnd().get()
                : other.effectiveEnd().get();

        return Optional.of(DateRange.closed(newStart, newEnd).withStep(this.step));
    }

    /**
     * Returns the total count of stepping elements in this range.
     */
    public long count() {
        return stream().count();
    }

    /**
     * First stepping date in this range.
     */
    public Optional<LocalDate> first() {
        Iterator<LocalDate> it = iterator();
        return it.hasNext() ? Optional.of(it.next()) : Optional.empty();
    }

    /**
     * Last stepping date in this range.
     */
    public Optional<LocalDate> last() {
        LocalDate last = null;
        for (LocalDate d : this) {
            last = d;
        }
        return Optional.ofNullable(last);
    }

    /**
     * Collects all stepping dates into an unmodifiable {@link List}.
     */
    public List<LocalDate> toList() {
        return stream().toList();
    }

    /**
     * Returns a sequential {@link Stream} of stepping dates across this range.
     */
    public Stream<LocalDate> stream() {
        return StreamSupport.stream(spliterator(), false);
    }

    // =========================================================================
    // Iterable Implementation
    // =========================================================================

    @Override
    public Iterator<LocalDate> iterator() {
        if (isEmpty()) {
            return Collections.emptyIterator();
        }

        return new Iterator<>() {
            private LocalDate current = effectiveStart().orElse(null);
            private final LocalDate effectiveMax = effectiveEnd().orElse(null);

            @Override
            public boolean hasNext() {
                return current != null && effectiveMax != null && !current.isAfter(effectiveMax);
            }

            @Override
            public LocalDate next() {
                if (!hasNext()) {
                    throw new NoSuchElementException("No more dates in range");
                }
                LocalDate result = current;
                current = step.next(current);
                return result;
            }
        };
    }

    @Override
    public String toString() {
        return policy.openBracket() + start + ", " + end + policy.closeBracket()
                + (step.equals(DateStep.oneDay()) ? "" : " (step " + step.period() + ")");
    }
}
