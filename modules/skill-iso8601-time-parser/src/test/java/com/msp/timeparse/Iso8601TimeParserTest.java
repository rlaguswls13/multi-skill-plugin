package com.msp.timeparse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Iso8601TimeParser Tests")
class Iso8601TimeParserTest {

    private final Instant expectedInstant = Instant.parse("2026-10-30T12:00:00Z");

    @Test
    @DisplayName("Parse standard ISO-8601 with UTC Z")
    void testIsoInstant() {
        Optional<Instant> instant = Iso8601TimeParser.parseToInstant("2026-10-30T12:00:00Z");
        assertTrue(instant.isPresent());
        assertEquals(expectedInstant, instant.get());
    }

    @Test
    @DisplayName("Parse ISO-8601 with timezone offset (+09:00)")
    void testIsoWithOffset() {
        // 21:00 in +09:00 is 12:00 UTC
        Optional<Instant> instant = Iso8601TimeParser.parseToInstant("2026-10-30T21:00:00+09:00");
        assertTrue(instant.isPresent());
        assertEquals(expectedInstant, instant.get());

        Optional<ZonedDateTime> zdt = Iso8601TimeParser.parseToZonedDateTime("2026-10-30T21:00:00+09:00", ZoneOffset.UTC);
        assertTrue(zdt.isPresent());
        assertEquals(ZoneOffset.ofHours(9), zdt.get().getOffset());
        assertEquals(21, zdt.get().getHour());
    }

    @Test
    @DisplayName("Parse RFC-1123 HTTP date")
    void testRfc1123() {
        Optional<Instant> instant = Iso8601TimeParser.parseToInstant("Fri, 30 Oct 2026 12:00:00 GMT");
        assertTrue(instant.isPresent());
        assertEquals(expectedInstant, instant.get());
    }

    @Test
    @DisplayName("Parse yyyy-MM-dd HH:mm:ss with default UTC")
    void testStandardDashFormat() {
        Optional<Instant> instant = Iso8601TimeParser.parseToInstant("2026-10-30 12:00:00", ZoneOffset.UTC);
        assertTrue(instant.isPresent());
        assertEquals(expectedInstant, instant.get());

        // With milliseconds
        Optional<Instant> withMillis = Iso8601TimeParser.parseToInstant("2026-10-30 12:00:00.500", ZoneOffset.UTC);
        assertTrue(withMillis.isPresent());
        assertEquals(expectedInstant.plusMillis(500), withMillis.get());
    }

    @Test
    @DisplayName("Parse slash and dot separated date times")
    void testSlashAndDotFormats() {
        Optional<Instant> slash = Iso8601TimeParser.parseToInstant("2026/10/30 12:00:00", ZoneOffset.UTC);
        assertTrue(slash.isPresent());
        assertEquals(expectedInstant, slash.get());

        Optional<Instant> dot = Iso8601TimeParser.parseToInstant("2026.10.30 12:00:00", ZoneOffset.UTC);
        assertTrue(dot.isPresent());
        assertEquals(expectedInstant, dot.get());
    }

    @Test
    @DisplayName("Parse compact date and time yyyyMMdd and yyyyMMddHHmmss")
    void testCompactFormats() {
        Optional<LocalDate> date = Iso8601TimeParser.parseToLocalDate("20261030");
        assertTrue(date.isPresent());
        assertEquals(LocalDate.of(2026, 10, 30), date.get());

        Optional<Instant> dt = Iso8601TimeParser.parseToInstant("20261030120000", ZoneOffset.UTC);
        assertTrue(dt.isPresent());
        assertEquals(expectedInstant, dt.get());
    }

    @Test
    @DisplayName("Parse epoch timestamps (seconds and milliseconds)")
    void testEpochTimestamps() {
        long epochSec = expectedInstant.getEpochSecond();
        Optional<Instant> fromSec = Iso8601TimeParser.parseToInstant(String.valueOf(epochSec));
        assertTrue(fromSec.isPresent());
        assertEquals(expectedInstant, fromSec.get());

        long epochMilli = expectedInstant.toEpochMilli();
        Optional<Instant> fromMilli = Iso8601TimeParser.parseToInstant(String.valueOf(epochMilli));
        assertTrue(fromMilli.isPresent());
        assertEquals(expectedInstant, fromMilli.get());
    }

    @Test
    @DisplayName("Parse date-only strings into LocalDate")
    void testDateOnly() {
        assertEquals(Optional.of(LocalDate.of(2026, 10, 30)), Iso8601TimeParser.parseToLocalDate("2026-10-30"));
        assertEquals(Optional.of(LocalDate.of(2026, 10, 30)), Iso8601TimeParser.parseToLocalDate("2026/10/30"));
        assertEquals(Optional.of(LocalDate.of(2026, 10, 30)), Iso8601TimeParser.parseToLocalDate("2026.10.30"));
        assertEquals(Optional.of(LocalDate.of(2026, 10, 30)), Iso8601TimeParser.parseToLocalDate("20261030"));
    }

    @Test
    @DisplayName("Handles quoted strings and whitespace properly")
    void testQuotedAndWhitespace() {
        Optional<Instant> instant = Iso8601TimeParser.parseToInstant("  \"2026-10-30T12:00:00Z\"  ");
        assertTrue(instant.isPresent());
        assertEquals(expectedInstant, instant.get());
    }

    @Test
    @DisplayName("Handles invalid, blank, or null inputs safely")
    void testInvalidInputs() {
        assertTrue(Iso8601TimeParser.parseToInstant(null).isEmpty());
        assertTrue(Iso8601TimeParser.parseToInstant("").isEmpty());
        assertTrue(Iso8601TimeParser.parseToInstant("   ").isEmpty());
        assertTrue(Iso8601TimeParser.parseToInstant("not-a-date").isEmpty());
        assertTrue(Iso8601TimeParser.parseToInstant("2026-99-99").isEmpty());
    }
}
