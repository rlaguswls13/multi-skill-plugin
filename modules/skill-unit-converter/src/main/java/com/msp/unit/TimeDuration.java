package com.msp.unit;

import java.time.Duration;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Immutable representation of a high-resolution time duration.
 * <p>
 * Supports composite human string parsing ("2h 30m", "1d 4h 30s"), unit cross-conversions,
 * human-readable formatting, and interoperability with {@link java.time.Duration}.
 */
public final class TimeDuration implements Comparable<TimeDuration> {

    public static final TimeDuration ZERO = new TimeDuration(0L);

    private static final Pattern TOKEN_PATTERN =
            Pattern.compile("([+-]?[0-9]+(?:\\.[0-9]+)?)\\s*([a-zA-Zµ]+)");

    private final long nanos;

    private TimeDuration(long nanos) {
        this.nanos = nanos;
    }

    public static TimeDuration ofNanos(long nanos) {
        return new TimeDuration(nanos);
    }

    public static TimeDuration ofMicros(double micros) {
        return of(micros, TimeDurationUnit.MICROS);
    }

    public static TimeDuration ofMillis(double millis) {
        return of(millis, TimeDurationUnit.MILLIS);
    }

    public static TimeDuration ofSeconds(double seconds) {
        return of(seconds, TimeDurationUnit.SECONDS);
    }

    public static TimeDuration ofMinutes(double minutes) {
        return of(minutes, TimeDurationUnit.MINUTES);
    }

    public static TimeDuration ofHours(double hours) {
        return of(hours, TimeDurationUnit.HOURS);
    }

    public static TimeDuration ofDays(double days) {
        return of(days, TimeDurationUnit.DAYS);
    }

    public static TimeDuration of(double amount, TimeDurationUnit unit) {
        Objects.requireNonNull(unit, "unit cannot be null");
        return new TimeDuration(Math.round(amount * unit.getNanosMultiplier()));
    }

    public static TimeDuration from(Duration duration) {
        Objects.requireNonNull(duration, "duration cannot be null");
        return new TimeDuration(duration.toNanos());
    }

    /**
     * Parses simple or composite human duration strings like "2h 30m", "1d 4h 30s", "100ms", "1.5h".
     */
    public static TimeDuration parse(String text) {
        Objects.requireNonNull(text, "text cannot be null");
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Cannot parse empty duration text");
        }

        Matcher matcher = TOKEN_PATTERN.matcher(trimmed);
        boolean matchedAny = false;
        long totalNanos = 0;
        int lastEnd = 0;

        while (matcher.find()) {
            matchedAny = true;
            String gap = trimmed.substring(lastEnd, matcher.start()).trim();
            if (!gap.isEmpty()) {
                throw new IllegalArgumentException("Unexpected token in duration text: '" + gap + "' in \"" + text + "\"");
            }
            lastEnd = matcher.end();

            double value = Double.parseDouble(matcher.group(1));
            String unitStr = matcher.group(2);
            TimeDurationUnit unit = TimeDurationUnit.fromSymbol(unitStr);
            totalNanos += Math.round(value * unit.getNanosMultiplier());
        }

        String remaining = trimmed.substring(lastEnd).trim();
        if (!matchedAny || !remaining.isEmpty()) {
            throw new IllegalArgumentException("Cannot parse duration: \"" + text + "\"");
        }

        return new TimeDuration(totalNanos);
    }

    public long toNanos() {
        return nanos;
    }

    public double to(TimeDurationUnit unit) {
        Objects.requireNonNull(unit, "unit cannot be null");
        return (double) nanos / unit.getNanosMultiplier();
    }

    public double toMicros() {
        return to(TimeDurationUnit.MICROS);
    }

    public double toMillis() {
        return to(TimeDurationUnit.MILLIS);
    }

    public double toSeconds() {
        return to(TimeDurationUnit.SECONDS);
    }

    public double toMinutes() {
        return to(TimeDurationUnit.MINUTES);
    }

    public double toHours() {
        return to(TimeDurationUnit.HOURS);
    }

    public double toDays() {
        return to(TimeDurationUnit.DAYS);
    }

    public Duration toJavaDuration() {
        return Duration.ofNanos(nanos);
    }

    /**
     * Formats this duration into a compact human-readable string (e.g. "2h 30m", "45s", "500ms").
     */
    public String toHumanReadable() {
        long absNanos = Math.abs(nanos);
        String prefix = nanos < 0 ? "-" : "";

        if (absNanos == 0) {
            return "0s";
        }
        if (absNanos < 1_000L) {
            return prefix + absNanos + "ns";
        }
        if (absNanos < 1_000_000L) {
            return prefix + (absNanos / 1_000L) + "us";
        }
        if (absNanos < 1_000_000_000L) {
            return prefix + (absNanos / 1_000_000L) + "ms";
        }

        long totalSeconds = absNanos / 1_000_000_000L;
        long days = totalSeconds / 86400L;
        long hours = (totalSeconds % 86400L) / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;

        StringBuilder sb = new StringBuilder(prefix);
        if (days > 0) {
            sb.append(days).append("d");
            if (hours > 0) sb.append(" ").append(hours).append("h");
            return sb.toString();
        }
        if (hours > 0) {
            sb.append(hours).append("h");
            if (minutes > 0) sb.append(" ").append(minutes).append("m");
            return sb.toString();
        }
        if (minutes > 0) {
            sb.append(minutes).append("m");
            if (seconds > 0) sb.append(" ").append(seconds).append("s");
            return sb.toString();
        }

        return prefix + seconds + "s";
    }

    public TimeDuration plus(TimeDuration other) {
        Objects.requireNonNull(other, "other cannot be null");
        return new TimeDuration(Math.addExact(this.nanos, other.nanos));
    }

    public TimeDuration minus(TimeDuration other) {
        Objects.requireNonNull(other, "other cannot be null");
        return new TimeDuration(Math.subtractExact(this.nanos, other.nanos));
    }

    @Override
    public int compareTo(TimeDuration o) {
        return Long.compare(this.nanos, o.nanos);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TimeDuration that)) return false;
        return nanos == that.nanos;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(nanos);
    }

    @Override
    public String toString() {
        return toHumanReadable();
    }
}
