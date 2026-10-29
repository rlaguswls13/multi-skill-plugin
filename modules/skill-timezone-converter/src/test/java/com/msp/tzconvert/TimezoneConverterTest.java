package com.msp.tzconvert;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TimezoneConverter Tests")
class TimezoneConverterTest {

    @Test
    @DisplayName("Resolve standard timezones and aliases")
    void testResolveZone() {
        assertEquals(ZoneId.of("Asia/Seoul"), TimezoneConverter.resolveZone("KST"));
        assertEquals(ZoneId.of("Asia/Tokyo"), TimezoneConverter.resolveZone("JST"));
        assertEquals(ZoneId.of("UTC"), TimezoneConverter.resolveZone("UTC"));
        assertEquals(ZoneId.of("America/Los_Angeles"), TimezoneConverter.resolveZone("PST"));
        assertEquals(ZoneId.of("America/New_York"), TimezoneConverter.resolveZone("EST"));
        assertEquals(ZoneId.of("Europe/London"), TimezoneConverter.resolveZone("BST"));
        assertEquals(ZoneId.of("Europe/Paris"), TimezoneConverter.resolveZone("CET"));
        assertEquals(ZoneId.of("Asia/Seoul"), TimezoneConverter.resolveZone("Asia/Seoul"));
        assertEquals(ZoneOffset.ofHours(9), TimezoneConverter.resolveZone("+09:00"));
    }

    @Test
    @DisplayName("Convert ZonedDateTime across timezones preserving Instant")
    void testConvertZonedDateTime() {
        ZonedDateTime utcTime = ZonedDateTime.of(2026, 10, 30, 12, 0, 0, 0, ZoneId.of("UTC"));
        ZonedDateTime kstTime = TimezoneConverter.convert(utcTime, "KST");

        assertEquals(21, kstTime.getHour());
        assertEquals(30, kstTime.getDayOfMonth());
        assertEquals(utcTime.toInstant(), kstTime.toInstant());

        // Convert KST to PST (UTC-7 during Daylight Saving)
        ZonedDateTime pstTime = TimezoneConverter.convert(kstTime, "PST");
        assertEquals(5, pstTime.getHour()); // 12:00 UTC - 7h = 05:00 PDT
        assertEquals(utcTime.toInstant(), pstTime.toInstant());
    }

    @Test
    @DisplayName("Convert Instant to target zone")
    void testConvertInstant() {
        Instant instant = Instant.parse("2026-10-30T12:00:00Z");
        ZonedDateTime kst = TimezoneConverter.convert(instant, "KST");

        assertEquals(2026, kst.getYear());
        assertEquals(10, kst.getMonthValue());
        assertEquals(30, kst.getDayOfMonth());
        assertEquals(21, kst.getHour());
        assertEquals(ZoneId.of("Asia/Seoul"), kst.getZone());
    }

    @Test
    @DisplayName("Calculate offset difference between two timezones")
    void testOffsetDifference() {
        Instant winter = Instant.parse("2026-01-15T12:00:00Z");
        Instant summer = Instant.parse("2026-07-15T12:00:00Z");

        // UTC -> KST is always +9 hours
        assertEquals(Duration.ofHours(9), TimezoneConverter.getOffsetDifference("UTC", "KST", winter));
        assertEquals(Duration.ofHours(-9), TimezoneConverter.getOffsetDifference("KST", "UTC", winter));

        // America/New_York (EST = UTC-5 in winter, EDT = UTC-4 in summer)
        // UTC -> EST in winter is -5 hours
        assertEquals(Duration.ofHours(-5), TimezoneConverter.getOffsetDifference("UTC", "America/New_York", winter));
        // UTC -> EDT in summer is -4 hours
        assertEquals(Duration.ofHours(-4), TimezoneConverter.getOffsetDifference("UTC", "America/New_York", summer));

        // KST (+9) vs America/New_York (-5 in winter) -> -14 hours difference
        assertEquals(Duration.ofHours(-14), TimezoneConverter.getOffsetDifference("KST", "America/New_York", winter));
    }

    @Test
    @DisplayName("Detect Daylight Saving Time (DST) correctly")
    void testDaylightSavingTime() {
        ZoneId nyZone = ZoneId.of("America/New_York");
        ZonedDateTime winterTime = ZonedDateTime.of(2026, 1, 15, 12, 0, 0, 0, nyZone);
        ZonedDateTime summerTime = ZonedDateTime.of(2026, 7, 15, 12, 0, 0, 0, nyZone);

        assertFalse(TimezoneConverter.isDaylightSavingTime(winterTime));
        assertTrue(TimezoneConverter.isDaylightSavingTime(summerTime));

        // Korea does not observe DST
        ZonedDateTime kstTime = ZonedDateTime.of(2026, 7, 15, 12, 0, 0, 0, ZoneId.of("Asia/Seoul"));
        assertFalse(TimezoneConverter.isDaylightSavingTime(kstTime));
    }

    @Test
    @DisplayName("Format ZonedDateTime to ISO and custom string patterns")
    void testFormatting() {
        ZonedDateTime kst = ZonedDateTime.of(2026, 10, 30, 21, 0, 0, 0, ZoneId.of("Asia/Seoul"));

        assertTrue(TimezoneConverter.formatIso(kst).startsWith("2026-10-30T21:00:00+09:00"));
        assertEquals("2026/10/30 21:00:00 KST", TimezoneConverter.format(kst, "yyyy/MM/dd HH:mm:ss z"));
    }
}
