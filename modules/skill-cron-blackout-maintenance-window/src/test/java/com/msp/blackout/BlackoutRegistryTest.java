package com.msp.blackout;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class BlackoutRegistryTest {

    @Test
    void oneTimeWindowBoundariesAndPolicies() {
        BlackoutRegistry registry = new BlackoutRegistry();
        Instant start = Instant.parse("2026-11-20T01:00:00Z");
        Instant end = Instant.parse("2026-11-20T03:00:00Z");
        registry.register(new BlackoutWindow.OneTimeWindow("db-upgrade", "Primary DB upgrade", start, end));

        assertFalse(registry.isInBlackout(start.minusSeconds(1)));
        assertTrue(registry.isInBlackout(start)); // inclusive
        assertTrue(registry.isInBlackout(Instant.parse("2026-11-20T02:15:00Z")));
        assertFalse(registry.isInBlackout(end)); // exclusive

        Instant mid = Instant.parse("2026-11-20T02:00:00Z");
        assertEquals(Optional.of(end), registry.schedule(mid, BlackoutPolicy.DELAY));
        assertEquals(Optional.empty(), registry.schedule(mid, BlackoutPolicy.SKIP));
        assertThrows(BlackoutConflictException.class, () -> registry.schedule(mid, BlackoutPolicy.REJECT));

        Instant allowed = Instant.parse("2026-11-20T04:00:00Z");
        assertEquals(Optional.of(allowed), registry.schedule(allowed, BlackoutPolicy.REJECT));
    }

    @Test
    void dailyOvernightWindowWithTimezone() {
        ZoneId seoul = ZoneId.of("Asia/Seoul"); // UTC+9
        BlackoutRegistry registry = new BlackoutRegistry();
        // 23:00 to 02:00 KST -> 14:00 to 17:00 UTC
        registry.register(new BlackoutWindow.DailyWindow(
                "nightly-batch", "Nightly settlement", LocalTime.of(23, 0), LocalTime.of(2, 0), seoul));

        // 2026-11-19 23:30 KST == 2026-11-19 14:30 UTC
        Instant lateNight = Instant.parse("2026-11-19T14:30:00Z");
        assertTrue(registry.isInBlackout(lateNight));
        assertEquals(Instant.parse("2026-11-19T17:00:00Z"), registry.nextAllowedTime(lateNight));

        // 2026-11-20 01:15 KST == 2026-11-19 16:15 UTC (after midnight KST, still in window started Nov 19!)
        Instant afterMidnight = Instant.parse("2026-11-19T16:15:00Z");
        assertTrue(registry.isInBlackout(afterMidnight));
        assertEquals(Instant.parse("2026-11-19T17:00:00Z"), registry.nextAllowedTime(afterMidnight));

        // 2026-11-20 02:00 KST == 2026-11-19 17:00 UTC -> allowed
        assertFalse(registry.isInBlackout(Instant.parse("2026-11-19T17:00:00Z")));
    }

    @Test
    void weeklyOvernightWindowAcrossWeekend() {
        BlackoutRegistry registry = new BlackoutRegistry();
        // Saturday 22:00 UTC to Sunday 04:00 UTC
        registry.register(new BlackoutWindow.WeeklyWindow(
                "weekend-maint", "Weekend maintenance",
                Set.of(DayOfWeek.SATURDAY),
                LocalTime.of(22, 0), LocalTime.of(4, 0), ZoneOffset.UTC));

        // 2026-11-21 is Saturday, 2026-11-22 is Sunday
        Instant satNight = Instant.parse("2026-11-21T23:00:00Z");
        Instant sunEarly = Instant.parse("2026-11-22T03:30:00Z");
        Instant sunMorning = Instant.parse("2026-11-22T04:00:00Z");
        Instant friNight = Instant.parse("2026-11-20T23:00:00Z");

        assertTrue(registry.isInBlackout(satNight));
        assertTrue(registry.isInBlackout(sunEarly));
        assertFalse(registry.isInBlackout(sunMorning));
        assertFalse(registry.isInBlackout(friNight));
        assertEquals(sunMorning, registry.nextAllowedTime(satNight));
        assertEquals(sunMorning, registry.nextAllowedTime(sunEarly));
    }

    @Test
    void cascadingOverlappingAndAdjacentWindowsResolveToFinalEnd() {
        BlackoutRegistry registry = new BlackoutRegistry();
        registry.register(new BlackoutWindow.OneTimeWindow(
                "w1", "Window 1", Instant.parse("2026-11-20T01:00:00Z"), Instant.parse("2026-11-20T02:00:00Z")));
        registry.register(new BlackoutWindow.OneTimeWindow(
                "w2", "Window 2 overlapping", Instant.parse("2026-11-20T01:30:00Z"), Instant.parse("2026-11-20T03:00:00Z")));
        registry.register(new BlackoutWindow.OneTimeWindow(
                "w3", "Window 3 back-to-back", Instant.parse("2026-11-20T03:00:00Z"), Instant.parse("2026-11-20T04:30:00Z")));

        Instant t = Instant.parse("2026-11-20T01:45:00Z");
        List<BlackoutWindow> active = registry.activeWindows(t);
        assertEquals(List.of("w1", "w2"), active.stream().map(BlackoutWindow::id).toList());
        assertEquals(Instant.parse("2026-11-20T04:30:00Z"), registry.nextAllowedTime(t));

        assertTrue(registry.unregister("w3"));
        assertEquals(Instant.parse("2026-11-20T03:00:00Z"), registry.nextAllowedTime(t));
    }

    @Test
    void continuousFullDayBlackoutThrowsConflictExceptionInsteadOfLooping() {
        BlackoutRegistry registry = new BlackoutRegistry();
        registry.register(new BlackoutWindow.DailyWindow(
                "half1", "00-12", LocalTime.of(0, 0), LocalTime.of(12, 0), ZoneOffset.UTC));
        registry.register(new BlackoutWindow.DailyWindow(
                "half2", "12-00", LocalTime.of(12, 0), LocalTime.of(0, 0), ZoneOffset.UTC));

        assertThrows(BlackoutConflictException.class,
                () -> registry.nextAllowedTime(Instant.parse("2026-11-20T06:00:00Z")));
    }

    @Test
    void validationRejectsInvalidWindowDefinitions() {
        Instant now = Instant.parse("2026-11-20T00:00:00Z");
        assertThrows(IllegalArgumentException.class,
                () -> new BlackoutWindow.OneTimeWindow(" ", "desc", now, now.plusSeconds(60)));
        assertThrows(IllegalArgumentException.class,
                () -> new BlackoutWindow.OneTimeWindow("id", "desc", now, now));
        assertThrows(IllegalArgumentException.class,
                () -> new BlackoutWindow.DailyWindow("id", "desc", LocalTime.NOON, LocalTime.NOON, ZoneOffset.UTC));
        assertThrows(IllegalArgumentException.class,
                () -> new BlackoutWindow.WeeklyWindow("id", "desc", Set.of(), LocalTime.MIDNIGHT, LocalTime.NOON, ZoneOffset.UTC));
    }
}
