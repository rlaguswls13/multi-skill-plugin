package com.msp.rrule;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RFC 5545 Recurrence Rule (RRULE) Parser & Evaluator Tests")
class RecurrenceRuleTest {

    @Test
    @DisplayName("Parse daily recurrence rule and calculate occurrences")
    void testDailyRecurrence() {
        RecurrenceRule rule = RecurrenceRule.parse("RRULE:FREQ=DAILY;INTERVAL=1;COUNT=5");
        assertEquals(Frequency.DAILY, rule.getFreq());
        assertEquals(1, rule.getInterval());
        assertEquals(5, rule.getCount().orElseThrow());

        ZonedDateTime start = ZonedDateTime.of(2026, 11, 1, 10, 0, 0, 0, ZoneOffset.UTC);
        List<ZonedDateTime> occurrences = rule.getOccurrences(start, 10);

        assertEquals(5, occurrences.size());
        assertEquals(LocalDate.of(2026, 11, 1), occurrences.get(0).toLocalDate());
        assertEquals(LocalDate.of(2026, 11, 2), occurrences.get(1).toLocalDate());
        assertEquals(LocalDate.of(2026, 11, 3), occurrences.get(2).toLocalDate());
        assertEquals(LocalDate.of(2026, 11, 4), occurrences.get(3).toLocalDate());
        assertEquals(LocalDate.of(2026, 11, 5), occurrences.get(4).toLocalDate());
    }

    @Test
    @DisplayName("Weekly recurrence with multiple BYDAY (MO, WE, FR)")
    void testWeeklyByDay() {
        // Starts on Wednesday 2026-11-04
        ZonedDateTime start = ZonedDateTime.of(2026, 11, 4, 14, 30, 0, 0, ZoneOffset.UTC);
        RecurrenceRule rule = RecurrenceRule.parse("FREQ=WEEKLY;INTERVAL=1;BYDAY=MO,WE,FR;COUNT=6");

        List<ZonedDateTime> occurrences = rule.getOccurrences(start, 10);
        assertEquals(6, occurrences.size());

        // First occurrence is Wednesday Nov 4 (since it's start date)
        assertEquals(LocalDate.of(2026, 11, 4), occurrences.get(0).toLocalDate());
        assertEquals(DayOfWeek.WEDNESDAY, occurrences.get(0).getDayOfWeek());

        // Next is Friday Nov 6
        assertEquals(LocalDate.of(2026, 11, 6), occurrences.get(1).toLocalDate());
        assertEquals(DayOfWeek.FRIDAY, occurrences.get(1).getDayOfWeek());

        // Next week: Monday Nov 9, Wednesday Nov 11, Friday Nov 13, then Monday Nov 16
        assertEquals(LocalDate.of(2026, 11, 9), occurrences.get(2).toLocalDate());
        assertEquals(LocalDate.of(2026, 11, 11), occurrences.get(3).toLocalDate());
        assertEquals(LocalDate.of(2026, 11, 13), occurrences.get(4).toLocalDate());
        assertEquals(LocalDate.of(2026, 11, 16), occurrences.get(5).toLocalDate());
    }

    @Test
    @DisplayName("Monthly recurrence on the last Friday of each month (BYDAY=FR;BYSETPOS=-1)")
    void testMonthlyLastFriday() {
        ZonedDateTime start = ZonedDateTime.of(2026, 9, 1, 9, 0, 0, 0, ZoneOffset.UTC);
        RecurrenceRule rule = RecurrenceRule.parse("RRULE:FREQ=MONTHLY;BYDAY=FR;BYSETPOS=-1;COUNT=3");

        List<ZonedDateTime> occurrences = rule.getOccurrences(start, 10);
        assertEquals(3, occurrences.size());

        // Last Friday in Sep 2026 is Sep 25
        assertEquals(LocalDate.of(2026, 9, 25), occurrences.get(0).toLocalDate());
        assertEquals(DayOfWeek.FRIDAY, occurrences.get(0).getDayOfWeek());

        // Last Friday in Oct 2026 is Oct 30
        assertEquals(LocalDate.of(2026, 10, 30), occurrences.get(1).toLocalDate());
        assertEquals(DayOfWeek.FRIDAY, occurrences.get(1).getDayOfWeek());

        // Last Friday in Nov 2026 is Nov 27
        assertEquals(LocalDate.of(2026, 11, 27), occurrences.get(2).toLocalDate());
        assertEquals(DayOfWeek.FRIDAY, occurrences.get(2).getDayOfWeek());
    }

    @Test
    @DisplayName("Date range filtering [from, to] returns only occurrences within window")
    void testDateRangeWindow() {
        ZonedDateTime start = ZonedDateTime.of(2026, 1, 1, 8, 0, 0, 0, ZoneOffset.UTC);
        RecurrenceRule rule = RecurrenceRule.parse("FREQ=MONTHLY;INTERVAL=1");

        ZonedDateTime from = ZonedDateTime.of(2026, 5, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        ZonedDateTime to = ZonedDateTime.of(2026, 8, 1, 23, 59, 59, 0, ZoneOffset.UTC);

        List<ZonedDateTime> occurrences = rule.getOccurrences(start, from, to, 100);
        assertEquals(4, occurrences.size());
        assertEquals(LocalDate.of(2026, 5, 1), occurrences.get(0).toLocalDate());
        assertEquals(LocalDate.of(2026, 6, 1), occurrences.get(1).toLocalDate());
        assertEquals(LocalDate.of(2026, 7, 1), occurrences.get(2).toLocalDate());
        assertEquals(LocalDate.of(2026, 8, 1), occurrences.get(3).toLocalDate());
    }

    @Test
    @DisplayName("UNTIL cutoff halts recurrence generation")
    void testUntilCutoff() {
        ZonedDateTime start = ZonedDateTime.of(2026, 11, 1, 12, 0, 0, 0, ZoneOffset.UTC);
        RecurrenceRule rule = RecurrenceRule.parse("FREQ=DAILY;INTERVAL=1;UNTIL=20261103T235959Z");

        List<ZonedDateTime> occurrences = rule.getOccurrences(start, 100);
        assertEquals(3, occurrences.size());
        assertEquals(LocalDate.of(2026, 11, 1), occurrences.get(0).toLocalDate());
        assertEquals(LocalDate.of(2026, 11, 2), occurrences.get(1).toLocalDate());
        assertEquals(LocalDate.of(2026, 11, 3), occurrences.get(2).toLocalDate());
    }

    @Test
    @DisplayName("Fluent builder builds correct rule and produces valid RFC 5545 string")
    void testFluentBuilder() {
        RecurrenceRule rule = RecurrenceRule.builder()
                .frequency(Frequency.WEEKLY)
                .interval(2)
                .byDay(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY)
                .count(8)
                .build();

        String rrule = rule.toRRuleString();
        assertTrue(rrule.contains("FREQ=WEEKLY"));
        assertTrue(rrule.contains("INTERVAL=2"));
        assertTrue(rrule.contains("BYDAY=TU,TH"));
        assertTrue(rrule.contains("COUNT=8"));
    }
}
