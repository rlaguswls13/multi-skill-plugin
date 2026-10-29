package com.msp.unit;

import java.time.temporal.ChronoUnit;

/**
 * Standard time duration units supporting nanoseconds through days.
 */
public enum TimeDurationUnit {
    NANOS("ns", 1L, ChronoUnit.NANOS),
    MICROS("us", 1_000L, ChronoUnit.MICROS),
    MILLIS("ms", 1_000_000L, ChronoUnit.MILLIS),
    SECONDS("s", 1_000_000_000L, ChronoUnit.SECONDS),
    MINUTES("m", 60_000_000_000L, ChronoUnit.MINUTES),
    HOURS("h", 3_600_000_000_000L, ChronoUnit.HOURS),
    DAYS("d", 86_400_000_000_000L, ChronoUnit.DAYS);

    private final String symbol;
    private final long nanosMultiplier;
    private final ChronoUnit chronoUnit;

    TimeDurationUnit(String symbol, long nanosMultiplier, ChronoUnit chronoUnit) {
        this.symbol = symbol;
        this.nanosMultiplier = nanosMultiplier;
        this.chronoUnit = chronoUnit;
    }

    public String getSymbol() {
        return symbol;
    }

    public long getNanosMultiplier() {
        return nanosMultiplier;
    }

    public ChronoUnit toChronoUnit() {
        return chronoUnit;
    }

    public static TimeDurationUnit fromSymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("Symbol cannot be null or empty");
        }
        for (TimeDurationUnit u : values()) {
            if (u.symbol.equalsIgnoreCase(symbol) || u.name().equalsIgnoreCase(symbol)) {
                return u;
            }
        }
        return switch (symbol.toLowerCase()) {
            case "nano", "nanos", "nanosecond", "nanoseconds" -> NANOS;
            case "micro", "micros", "microsecond", "microseconds", "µs" -> MICROS;
            case "milli", "millis", "millisecond", "milliseconds" -> MILLIS;
            case "sec", "secs", "second", "seconds" -> SECONDS;
            case "min", "mins", "minute", "minutes" -> MINUTES;
            case "hr", "hrs", "hour", "hours" -> HOURS;
            case "day", "days" -> DAYS;
            default -> throw new IllegalArgumentException("Unknown time duration unit: " + symbol);
        };
    }
}
