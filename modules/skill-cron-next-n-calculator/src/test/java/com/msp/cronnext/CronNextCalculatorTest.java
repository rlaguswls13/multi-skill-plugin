package com.msp.cronnext;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CronNextCalculatorTest {

    private final ZoneId utc = ZoneId.of("UTC");

    @Test
    @DisplayName("Every 15 minutes cron generates correct next occurrences")
    void testEvery15Minutes() {
        ZonedDateTime start = ZonedDateTime.of(2026, 11, 1, 10, 7, 30, 0, utc);
        List<ZonedDateTime> next5 = CronNextCalculator.nextN("*/15 * * * *", start, 5);

        assertEquals(5, next5.size());
        assertEquals(ZonedDateTime.of(2026, 11, 1, 10, 15, 0, 0, utc), next5.get(0));
        assertEquals(ZonedDateTime.of(2026, 11, 1, 10, 30, 0, 0, utc), next5.get(1));
        assertEquals(ZonedDateTime.of(2026, 11, 1, 10, 45, 0, 0, utc), next5.get(2));
        assertEquals(ZonedDateTime.of(2026, 11, 1, 11, 0, 0, 0, utc), next5.get(3));
        assertEquals(ZonedDateTime.of(2026, 11, 1, 11, 15, 0, 0, utc), next5.get(4));
    }

    @Test
    @DisplayName("Specific time at 09:00 on weekdays (Mon-Fri)")
    void testWeekdaysMorning() {
        // 2026-11-06 is Friday
        ZonedDateTime fridayMorning = ZonedDateTime.of(2026, 11, 6, 9, 30, 0, 0, utc);
        // Next run should skip Saturday (7th) and Sunday (8th) and land on Monday (9th) at 09:00
        ZonedDateTime nextRun = CronNextCalculator.next("0 9 * * 1-5", fridayMorning);

        assertEquals(ZonedDateTime.of(2026, 11, 9, 9, 0, 0, 0, utc), nextRun);
    }

    @Test
    @DisplayName("Count 0 or negative returns empty list")
    void testCountZero() {
        ZonedDateTime start = ZonedDateTime.of(2026, 11, 1, 0, 0, 0, 0, utc);
        assertTrue(CronNextCalculator.nextN("* * * * *", start, 0).isEmpty());
        assertTrue(CronNextCalculator.nextN("* * * * *", start, -1).isEmpty());
    }

    @Test
    @DisplayName("Invalid cron expression throws IllegalArgumentException")
    void testInvalidCron() {
        ZonedDateTime start = ZonedDateTime.now();
        assertThrows(IllegalArgumentException.class, () -> CronNextCalculator.next("invalid cron", start));
        assertThrows(IllegalArgumentException.class, () -> CronNextCalculator.next("65 * * * *", start));
        assertThrows(IllegalArgumentException.class, () -> CronNextCalculator.next("* * * * 8", start));
    }
}
