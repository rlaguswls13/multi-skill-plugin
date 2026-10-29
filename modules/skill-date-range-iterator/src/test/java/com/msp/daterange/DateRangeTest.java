package com.msp.daterange;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class DateRangeTest {

    private final LocalDate start = LocalDate.of(2026, 1, 1);
    private final LocalDate end = LocalDate.of(2026, 1, 5);

    @Test
    @DisplayName("Closed interval [a, b] includes both endpoints")
    void testClosedInterval() {
        DateRange range = DateRange.closed(start, end);

        assertFalse(range.isEmpty());
        assertEquals(5, range.count());
        assertTrue(range.contains(start));
        assertTrue(range.contains(end));
        assertTrue(range.contains(LocalDate.of(2026, 1, 3)));
        assertFalse(range.contains(LocalDate.of(2025, 12, 31)));
        assertFalse(range.contains(LocalDate.of(2026, 1, 6)));

        List<LocalDate> dates = range.toList();
        assertEquals(List.of(
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 2),
                LocalDate.of(2026, 1, 3),
                LocalDate.of(2026, 1, 4),
                LocalDate.of(2026, 1, 5)
        ), dates);
    }

    @Test
    @DisplayName("Half-open interval [a, b) includes start but excludes end")
    void testHalfOpenInterval() {
        DateRange range = DateRange.halfOpen(start, end);

        assertEquals(4, range.count());
        assertTrue(range.contains(start));
        assertFalse(range.contains(end));
        assertEquals(Optional.of(LocalDate.of(2026, 1, 4)), range.effectiveEnd());

        List<LocalDate> dates = range.toList();
        assertEquals(4, dates.size());
        assertEquals(LocalDate.of(2026, 1, 4), dates.get(3));
    }

    @Test
    @DisplayName("Open interval (a, b) excludes both endpoints")
    void testOpenInterval() {
        DateRange range = DateRange.open(start, end);

        assertEquals(3, range.count());
        assertFalse(range.contains(start));
        assertFalse(range.contains(end));
        assertTrue(range.contains(LocalDate.of(2026, 1, 2)));
        assertTrue(range.contains(LocalDate.of(2026, 1, 4)));

        List<LocalDate> dates = range.toList();
        assertEquals(List.of(
                LocalDate.of(2026, 1, 2),
                LocalDate.of(2026, 1, 3),
                LocalDate.of(2026, 1, 4)
        ), dates);
    }

    @Test
    @DisplayName("Open-closed interval (a, b] excludes start and includes end")
    void testOpenClosedInterval() {
        DateRange range = DateRange.of(start, end, InclusionPolicy.OPEN_CLOSED);

        assertEquals(4, range.count());
        assertFalse(range.contains(start));
        assertTrue(range.contains(end));

        List<LocalDate> dates = range.toList();
        assertEquals(LocalDate.of(2026, 1, 2), dates.getFirst());
        assertEquals(LocalDate.of(2026, 1, 5), dates.getLast());
    }

    @Test
    @DisplayName("Custom step units (Days, Weeks, Months)")
    void testCustomStepUnits() {
        // Step of 2 days
        DateRange step2Days = DateRange.closed(start, end).withStep(2, ChronoUnit.DAYS);
        assertEquals(List.of(
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 3),
                LocalDate.of(2026, 1, 5)
        ), step2Days.toList());

        // Step of 1 week
        DateRange monthRange = DateRange.closed(
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 22)
        ).withStep(DateStep.ofWeeks(1));

        assertEquals(List.of(
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 8),
                LocalDate.of(2026, 1, 15),
                LocalDate.of(2026, 1, 22)
        ), monthRange.toList());

        // Step of 1 month
        DateRange quarterRange = DateRange.closed(
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 4, 1)
        ).withStep(DateStep.oneMonth());

        assertEquals(4, quarterRange.count());
        assertEquals(LocalDate.of(2026, 4, 1), quarterRange.last().orElseThrow());
    }

    @Test
    @DisplayName("Overlaps and Intersection between date ranges")
    void testOverlapsAndIntersection() {
        DateRange r1 = DateRange.closed(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 10));
        DateRange r2 = DateRange.closed(LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 15));
        DateRange r3 = DateRange.closed(LocalDate.of(2026, 1, 11), LocalDate.of(2026, 1, 20));

        assertTrue(r1.overlaps(r2));
        assertTrue(r2.overlaps(r1));
        assertFalse(r1.overlaps(r3));

        Optional<DateRange> intersection = r1.intersection(r2);
        assertTrue(intersection.isPresent());
        assertEquals(LocalDate.of(2026, 1, 5), intersection.get().start());
        assertEquals(LocalDate.of(2026, 1, 10), intersection.get().end());

        assertTrue(r1.intersection(r3).isEmpty());
    }

    @Test
    @DisplayName("Instant conversion with ZoneId")
    void testInstantOverloads() {
        ZoneId zoneKst = ZoneId.of("Asia/Seoul");
        // 2026-01-01 00:00:00 KST = 2025-12-31 15:00:00 UTC
        Instant i1 = Instant.parse("2025-12-31T15:00:00Z");
        Instant i2 = Instant.parse("2026-01-04T15:00:00Z");

        DateRange range = DateRange.closed(i1, i2, zoneKst);
        assertEquals(LocalDate.of(2026, 1, 1), range.start());
        assertEquals(LocalDate.of(2026, 1, 5), range.end());

        assertTrue(range.contains(Instant.parse("2026-01-02T12:00:00Z"), zoneKst));
    }

    @Test
    @DisplayName("Degenerate and empty ranges")
    void testDegenerateAndEmptyRanges() {
        // start > end is empty
        DateRange inverted = DateRange.closed(end, start);
        assertTrue(inverted.isEmpty());
        assertEquals(0, inverted.count());
        assertEquals(0, inverted.toList().size());

        // start == end
        DateRange singleDayClosed = DateRange.closed(start, start);
        assertFalse(singleDayClosed.isEmpty());
        assertEquals(1, singleDayClosed.count());
        assertEquals(start, singleDayClosed.first().orElseThrow());

        DateRange singleDayHalfOpen = DateRange.halfOpen(start, start);
        assertTrue(singleDayHalfOpen.isEmpty());
        assertEquals(0, singleDayHalfOpen.count());

        DateRange singleDayOpen = DateRange.open(start, start);
        assertTrue(singleDayOpen.isEmpty());
    }

    @Test
    @DisplayName("Iterator throws NoSuchElementException when exhausted")
    void testIteratorExhaustion() {
        DateRange range = DateRange.closed(start, start);
        var it = range.iterator();
        assertTrue(it.hasNext());
        assertEquals(start, it.next());
        assertFalse(it.hasNext());
        assertThrows(NoSuchElementException.class, it::next);
    }
}
