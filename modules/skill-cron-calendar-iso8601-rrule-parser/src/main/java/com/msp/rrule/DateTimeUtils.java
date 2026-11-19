package com.msp.rrule;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Parses iCalendar date-time strings (RFC 5545 / ISO-8601) into ZonedDateTime.
 */
public final class DateTimeUtils {

    private static final DateTimeFormatter BASIC_DT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");
    private static final DateTimeFormatter BASIC_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private DateTimeUtils() {
    }

    public static ZonedDateTime parseDateTime(String text, ZoneId defaultZone) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String s = text.trim();
        if (s.endsWith("Z")) {
            String sub = s.substring(0, s.length() - 1);
            if (sub.contains("-")) {
                // ISO 8601 extended (e.g. 2026-12-31T23:59:59Z)
                return ZonedDateTime.parse(s);
            } else {
                // RFC 5545 basic (e.g. 20261231T235959Z)
                LocalDateTime ldt = LocalDateTime.parse(sub, BASIC_DT);
                return ldt.atZone(ZoneOffset.UTC);
            }
        }

        if (s.contains("T")) {
            if (s.contains("-")) {
                LocalDateTime ldt = LocalDateTime.parse(s);
                return ldt.atZone(defaultZone);
            } else {
                LocalDateTime ldt = LocalDateTime.parse(s, BASIC_DT);
                return ldt.atZone(defaultZone);
            }
        } else {
            // Date-only
            LocalDate ld;
            if (s.contains("-")) {
                ld = LocalDate.parse(s);
            } else {
                ld = LocalDate.parse(s, BASIC_DATE);
            }
            return ld.atTime(23, 59, 59).atZone(defaultZone);
        }
    }
}
