package com.msp.rrule;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Representation of a BYDAY entry with optional ordinal prefix according to RFC 5545.
 * E.g., "FR" (ordinal 0), "1MO" (ordinal 1, first Monday), "-1FR" (ordinal -1, last Friday).
 */
public record ByDay(int ordinal, DayOfWeek dayOfWeek) {

    public ByDay {
        Objects.requireNonNull(dayOfWeek, "dayOfWeek must not be null");
    }

    public static ByDay of(DayOfWeek dayOfWeek) {
        return new ByDay(0, dayOfWeek);
    }

    public static ByDay of(int ordinal, DayOfWeek dayOfWeek) {
        return new ByDay(ordinal, dayOfWeek);
    }

    public static ByDay parse(String text) {
        Objects.requireNonNull(text, "text must not be null");
        String s = text.trim().toUpperCase();
        if (s.length() < 2) {
            throw new IllegalArgumentException("Invalid BYDAY value: " + text);
        }

        String dayPart = s.substring(s.length() - 2);
        DayOfWeek dow = parseDayOfWeek(dayPart);

        int ordinal = 0;
        if (s.length() > 2) {
            String ordPart = s.substring(0, s.length() - 2);
            if (ordPart.startsWith("+")) {
                ordPart = ordPart.substring(1);
            }
            try {
                ordinal = Integer.parseInt(ordPart);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid BYDAY ordinal in: " + text, e);
            }
        }
        return new ByDay(ordinal, dow);
    }

    public boolean matches(LocalDate date) {
        if (date.getDayOfWeek() != dayOfWeek) {
            return false;
        }
        if (ordinal == 0) {
            return true;
        }
        if (ordinal > 0) {
            int nth = (date.getDayOfMonth() - 1) / 7 + 1;
            return nth == ordinal;
        } else {
            // Negative ordinal: -1 means last occurrence in month
            int daysLeftInMonth = date.lengthOfMonth() - date.getDayOfMonth();
            int nthFromEnd = -(daysLeftInMonth / 7 + 1);
            return nthFromEnd == ordinal;
        }
    }

    private static DayOfWeek parseDayOfWeek(String s) {
        return switch (s) {
            case "MO" -> DayOfWeek.MONDAY;
            case "TU" -> DayOfWeek.TUESDAY;
            case "WE" -> DayOfWeek.WEDNESDAY;
            case "TH" -> DayOfWeek.THURSDAY;
            case "FR" -> DayOfWeek.FRIDAY;
            case "SA" -> DayOfWeek.SATURDAY;
            case "SU" -> DayOfWeek.SUNDAY;
            default -> throw new IllegalArgumentException("Unknown day of week code: " + s);
        };
    }

    public static String toDayOfWeekCode(DayOfWeek dow) {
        return switch (dow) {
            case MONDAY -> "MO";
            case TUESDAY -> "TU";
            case WEDNESDAY -> "WE";
            case THURSDAY -> "TH";
            case FRIDAY -> "FR";
            case SATURDAY -> "SA";
            case SUNDAY -> "SU";
        };
    }

    @Override
    public String toString() {
        if (ordinal == 0) {
            return toDayOfWeekCode(dayOfWeek);
        }
        return ordinal + toDayOfWeekCode(dayOfWeek);
    }
}
