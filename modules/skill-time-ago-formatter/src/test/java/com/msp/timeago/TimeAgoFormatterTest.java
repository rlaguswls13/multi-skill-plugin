package com.msp.timeago;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("TimeAgoFormatter Tests")
class TimeAgoFormatterTest {

    private final Instant fixedNow = Instant.parse("2026-10-30T12:00:00Z");
    private final TimeAgoFormatter formatter = TimeAgoFormatter.builder()
            .clock(Clock.fixed(fixedNow, ZoneId.of("UTC")))
            .zoneId(ZoneId.of("UTC"))
            .defaultLocale(Locale.KOREAN)
            .maxRelativeDays(30)
            .build();

    @Test
    @DisplayName("Korean relative past times formatting")
    void testKoreanPast() {
        // Just now (< 5s)
        assertEquals("방금 전", formatter.format(fixedNow.minusSeconds(3), Locale.KOREAN));

        // Seconds (< 60s)
        assertEquals("10초 전", formatter.format(fixedNow.minusSeconds(10), Locale.KOREAN));
        assertEquals("45초 전", formatter.format(fixedNow.minusSeconds(45), Locale.KOREAN));

        // Minutes (< 60m)
        assertEquals("1분 전", formatter.format(fixedNow.minus(Duration.ofMinutes(1)), Locale.KOREAN));
        assertEquals("15분 전", formatter.format(fixedNow.minus(Duration.ofMinutes(15)), Locale.KOREAN));

        // Hours (< 24h)
        assertEquals("1시간 전", formatter.format(fixedNow.minus(Duration.ofHours(1)), Locale.KOREAN));
        assertEquals("5시간 전", formatter.format(fixedNow.minus(Duration.ofHours(5)), Locale.KOREAN));

        // Yesterday (1 day)
        assertEquals("어제", formatter.format(fixedNow.minus(Duration.ofDays(1)), Locale.KOREAN));

        // Days (< 30d)
        assertEquals("3일 전", formatter.format(fixedNow.minus(Duration.ofDays(3)), Locale.KOREAN));
        assertEquals("20일 전", formatter.format(fixedNow.minus(Duration.ofDays(20)), Locale.KOREAN));

        // Fallback to absolute date (> 30d)
        assertEquals("2026-09-25", formatter.format(fixedNow.minus(Duration.ofDays(35)), Locale.KOREAN));
    }

    @Test
    @DisplayName("English relative past times formatting")
    void testEnglishPast() {
        // Just now
        assertEquals("just now", formatter.format(fixedNow.minusSeconds(2), Locale.ENGLISH));

        // Seconds
        assertEquals("10 seconds ago", formatter.format(fixedNow.minusSeconds(10), Locale.ENGLISH));

        // Minutes
        assertEquals("1 minute ago", formatter.format(fixedNow.minus(Duration.ofMinutes(1)), Locale.ENGLISH));
        assertEquals("30 minutes ago", formatter.format(fixedNow.minus(Duration.ofMinutes(30)), Locale.ENGLISH));

        // Hours
        assertEquals("1 hour ago", formatter.format(fixedNow.minus(Duration.ofHours(1)), Locale.ENGLISH));
        assertEquals("8 hours ago", formatter.format(fixedNow.minus(Duration.ofHours(8)), Locale.ENGLISH));

        // Yesterday
        assertEquals("yesterday", formatter.format(fixedNow.minus(Duration.ofDays(1)), Locale.ENGLISH));

        // Days
        assertEquals("5 days ago", formatter.format(fixedNow.minus(Duration.ofDays(5)), Locale.ENGLISH));

        // Fallback
        assertEquals("2026-09-25", formatter.format(fixedNow.minus(Duration.ofDays(35)), Locale.ENGLISH));
    }

    @Test
    @DisplayName("Future times formatting")
    void testFutureTimes() {
        // Just now / in a moment
        assertEquals("곧", formatter.format(fixedNow.plusSeconds(3), Locale.KOREAN));
        assertEquals("in a moment", formatter.format(fixedNow.plusSeconds(3), Locale.ENGLISH));

        // Seconds
        assertEquals("15초 후", formatter.format(fixedNow.plusSeconds(15), Locale.KOREAN));
        assertEquals("in 15 seconds", formatter.format(fixedNow.plusSeconds(15), Locale.ENGLISH));

        // Minutes
        assertEquals("10분 후", formatter.format(fixedNow.plus(Duration.ofMinutes(10)), Locale.KOREAN));
        assertEquals("in 10 minutes", formatter.format(fixedNow.plus(Duration.ofMinutes(10)), Locale.ENGLISH));

        // Hours
        assertEquals("3시간 후", formatter.format(fixedNow.plus(Duration.ofHours(3)), Locale.KOREAN));
        assertEquals("in 3 hours", formatter.format(fixedNow.plus(Duration.ofHours(3)), Locale.ENGLISH));

        // Tomorrow
        assertEquals("내일", formatter.format(fixedNow.plus(Duration.ofDays(1)), Locale.KOREAN));
        assertEquals("tomorrow", formatter.format(fixedNow.plus(Duration.ofDays(1)), Locale.ENGLISH));

        // Days
        assertEquals("7일 후", formatter.format(fixedNow.plus(Duration.ofDays(7)), Locale.KOREAN));
        assertEquals("in 7 days", formatter.format(fixedNow.plus(Duration.ofDays(7)), Locale.ENGLISH));
    }

    @Test
    @DisplayName("Disabled fallback shows months and years")
    void testMonthsAndYearsWithoutFallback() {
        TimeAgoFormatter unlimitedFormatter = TimeAgoFormatter.builder()
                .clock(Clock.fixed(fixedNow, ZoneId.of("UTC")))
                .maxRelativeDays(null)
                .build();

        // 60 days -> 2 months
        assertEquals("2개월 전", unlimitedFormatter.format(fixedNow.minus(Duration.ofDays(60)), Locale.KOREAN));
        assertEquals("2 months ago", unlimitedFormatter.format(fixedNow.minus(Duration.ofDays(60)), Locale.ENGLISH));

        // 400 days -> 1 year
        assertEquals("1년 전", unlimitedFormatter.format(fixedNow.minus(Duration.ofDays(400)), Locale.KOREAN));
        assertEquals("1 year ago", unlimitedFormatter.format(fixedNow.minus(Duration.ofDays(400)), Locale.ENGLISH));

        // 750 days -> 2 years
        assertEquals("2년 전", unlimitedFormatter.format(fixedNow.minus(Duration.ofDays(750)), Locale.KOREAN));
        assertEquals("2 years ago", unlimitedFormatter.format(fixedNow.minus(Duration.ofDays(750)), Locale.ENGLISH));
    }
}
