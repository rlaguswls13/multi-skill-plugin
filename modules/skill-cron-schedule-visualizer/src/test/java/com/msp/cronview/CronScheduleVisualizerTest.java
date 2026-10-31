package com.msp.cronview;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CronScheduleVisualizer Tests")
class CronScheduleVisualizerTest {

    private static final ZoneId UTC = ZoneId.of("UTC");

    @Test
    @DisplayName("Every minute cron expression '* * * * *'")
    void testEveryMinute() {
        ZonedDateTime start = ZonedDateTime.of(2026, 11, 1, 10, 0, 0, 0, UTC);
        List<ZonedDateTime> next = CronScheduleVisualizer.getNextExecutions("* * * * *", start, 3);

        assertEquals(3, next.size());
        assertEquals(ZonedDateTime.of(2026, 11, 1, 10, 1, 0, 0, UTC), next.get(0));
        assertEquals(ZonedDateTime.of(2026, 11, 1, 10, 2, 0, 0, UTC), next.get(1));
        assertEquals(ZonedDateTime.of(2026, 11, 1, 10, 3, 0, 0, UTC), next.get(2));
    }

    @Test
    @DisplayName("Step interval cron '*/15 * * * *'")
    void testEveryFifteenMinutes() {
        ZonedDateTime start = ZonedDateTime.of(2026, 11, 1, 10, 5, 30, 0, UTC);
        List<ZonedDateTime> next = CronScheduleVisualizer.getNextExecutions("*/15 * * * *", start, 3);

        assertEquals(3, next.size());
        assertEquals(ZonedDateTime.of(2026, 11, 1, 10, 15, 0, 0, UTC), next.get(0));
        assertEquals(ZonedDateTime.of(2026, 11, 1, 10, 30, 0, 0, UTC), next.get(1));
        assertEquals(ZonedDateTime.of(2026, 11, 1, 10, 45, 0, 0, UTC), next.get(2));
    }

    @Test
    @DisplayName("Weekday morning schedule '0 9 * * 1-5' (Mon-Fri)")
    void testWeekdaysOnly() {
        // 2026-11-06 is Friday
        ZonedDateTime fridayMorning = ZonedDateTime.of(2026, 11, 6, 9, 30, 0, 0, UTC);
        List<ZonedDateTime> next = CronScheduleVisualizer.getNextExecutions("0 9 * * 1-5", fridayMorning, 2);

        assertEquals(2, next.size());
        // Next trigger must be Monday 2026-11-09 (skipping Sat 7 and Sun 8)
        assertEquals(DayOfWeek.MONDAY, next.get(0).getDayOfWeek());
        assertEquals(ZonedDateTime.of(2026, 11, 9, 9, 0, 0, 0, UTC), next.get(0));

        // Subsequent trigger must be Tuesday 2026-11-10
        assertEquals(DayOfWeek.TUESDAY, next.get(1).getDayOfWeek());
        assertEquals(ZonedDateTime.of(2026, 11, 10, 9, 0, 0, 0, UTC), next.get(1));
    }

    @Test
    @DisplayName("Specific months and aliases e.g. '0 0 1 JAN,JUL *'")
    void testMonthAliases() {
        ZonedDateTime start = ZonedDateTime.of(2026, 2, 1, 0, 0, 0, 0, UTC);
        List<ZonedDateTime> next = CronScheduleVisualizer.getNextExecutions("0 0 1 JAN,JUL *", start, 2);

        assertEquals(2, next.size());
        assertEquals(ZonedDateTime.of(2026, 7, 1, 0, 0, 0, 0, UTC), next.get(0));
        assertEquals(ZonedDateTime.of(2027, 1, 1, 0, 0, 0, 0, UTC), next.get(1));
    }

    @Test
    @DisplayName("renderTimelineTable generates properly formatted ASCII table")
    void testRenderTimelineTable() {
        ZonedDateTime start = ZonedDateTime.of(2026, 11, 1, 8, 0, 0, 0, UTC);
        String table = CronScheduleVisualizer.renderTimelineTable("0 9 * * *", start, 3);

        assertNotNull(table);
        assertTrue(table.contains("Cron Schedule Timeline"));
        assertTrue(table.contains("0 9 * * *"));
        assertTrue(table.contains("Trigger Time"));
        assertTrue(table.contains("Interval from Previous"));
        assertTrue(table.contains("2026-11-01 09:00:00"));
        assertTrue(table.contains("2026-11-02 09:00:00"));
        assertTrue(table.contains("1d"));
    }

    @Test
    @DisplayName("renderDailyHeatmap generates 24-hour distribution bar chart")
    void testRenderDailyHeatmap() {
        ZonedDateTime start = ZonedDateTime.of(2026, 11, 1, 0, 0, 0, 0, UTC);
        // Runs at minute 0 and 30 between hours 9 to 17
        String heatmap = CronScheduleVisualizer.renderDailyHeatmap("0,30 9-17 * * *", start, 7);

        assertNotNull(heatmap);
        assertTrue(heatmap.contains("24-Hour Execution Heatmap"));
        assertTrue(heatmap.contains("Hour | Triggers | Distribution"));
        assertTrue(heatmap.contains("■"));
        assertTrue(heatmap.contains(" 09  |"));
        assertTrue(heatmap.contains(" 17  |"));
    }

    @Test
    @DisplayName("Validation and error handling for invalid cron expressions and parameters")
    void testInvalidCronExpressions() {
        ZonedDateTime now = ZonedDateTime.now(UTC);

        assertThrows(NullPointerException.class, () -> CronScheduleVisualizer.getNextExecutions(null, now, 5));
        assertThrows(NullPointerException.class, () -> CronScheduleVisualizer.getNextExecutions("* * * * *", null, 5));
        assertThrows(IllegalArgumentException.class, () -> CronScheduleVisualizer.getNextExecutions("* * * * *", now, 0));
        assertThrows(IllegalArgumentException.class, () -> CronScheduleVisualizer.getNextExecutions("* * * * *", now, -1));

        // Incorrect field count
        assertThrows(IllegalArgumentException.class, () -> CronScheduleVisualizer.getNextExecutions("* * *", now, 5));
        assertThrows(IllegalArgumentException.class, () -> CronScheduleVisualizer.getNextExecutions("* * * * * *", now, 5));

        // Out of range fields
        assertThrows(IllegalArgumentException.class, () -> CronScheduleVisualizer.getNextExecutions("60 * * * *", now, 5));
        assertThrows(IllegalArgumentException.class, () -> CronScheduleVisualizer.getNextExecutions("* 24 * * *", now, 5));
        assertThrows(IllegalArgumentException.class, () -> CronScheduleVisualizer.getNextExecutions("* * 32 * *", now, 5));
        assertThrows(IllegalArgumentException.class, () -> CronScheduleVisualizer.getNextExecutions("* * * 13 *", now, 5));
        assertThrows(IllegalArgumentException.class, () -> CronScheduleVisualizer.getNextExecutions("* * * * 8", now, 5));
        assertThrows(IllegalArgumentException.class, () -> CronScheduleVisualizer.getNextExecutions("*/0 * * * *", now, 5));
    }
}
