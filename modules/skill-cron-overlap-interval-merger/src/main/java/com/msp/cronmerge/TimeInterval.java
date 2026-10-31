package com.msp.cronmerge;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Immutable time interval defined by start and end instants [start, end].
 *
 * @param start start instant (inclusive)
 * @param end   end instant (inclusive / exclusive boundary)
 */
public record TimeInterval(Instant start, Instant end) implements Comparable<TimeInterval> {

    public TimeInterval {
        Objects.requireNonNull(start, "Start instant cannot be null");
        Objects.requireNonNull(end, "End instant cannot be null");
        if (start.isAfter(end)) {
            throw new IllegalArgumentException("Start instant " + start + " cannot be after end instant " + end);
        }
    }

    /**
     * Duration between start and end.
     */
    public Duration duration() {
        return Duration.between(start, end);
    }

    /**
     * Tests if this interval overlaps with another interval.
     */
    public boolean overlaps(TimeInterval other) {
        Objects.requireNonNull(other, "Other interval cannot be null");
        return !this.end.isBefore(other.start) && !other.end.isBefore(this.start);
    }

    /**
     * Tests if this interval is contiguous (touches at endpoints) with another interval.
     */
    public boolean isContiguousWith(TimeInterval other) {
        Objects.requireNonNull(other, "Other interval cannot be null");
        return this.end.equals(other.start) || other.end.equals(this.start);
    }

    /**
     * Merges this interval with an overlapping or contiguous interval.
     *
     * @param other interval to merge with
     * @return a single merged TimeInterval
     * @throws IllegalArgumentException if the intervals are disjoint
     */
    public TimeInterval merge(TimeInterval other) {
        Objects.requireNonNull(other, "Other interval cannot be null");
        if (!overlaps(other) && !isContiguousWith(other)) {
            throw new IllegalArgumentException("Cannot merge disjoint intervals: " + this + " and " + other);
        }
        Instant minStart = this.start.isBefore(other.start) ? this.start : other.start;
        Instant maxEnd = this.end.isAfter(other.end) ? this.end : other.end;
        return new TimeInterval(minStart, maxEnd);
    }

    @Override
    public int compareTo(TimeInterval other) {
        int cmp = this.start.compareTo(other.start);
        if (cmp != 0) {
            return cmp;
        }
        return this.end.compareTo(other.end);
    }
}
