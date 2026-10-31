package com.msp.cronbuilder;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.DayOfWeek;
import java.time.Month;

import static org.junit.jupiter.api.Assertions.*;

class CronNaturalBuilderTest {

    @Test
    @DisplayName("everyMinute 5-field and 6-field generation")
    void testEveryMinute() {
        assertEquals("* * * * *", CronBuilder.everyMinute().build());
        assertEquals("0 * * * * *", CronBuilder.everyMinute().withSeconds().build());
        assertEquals("30 * * * * *", CronBuilder.everyMinute().withSeconds(30).build());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 5, 10, 15, 30, 45, 59})
    @DisplayName("everyMinutes with various interval values")
    void testEveryMinutes(int interval) {
        assertEquals("*/" + interval + " * * * *", CronBuilder.everyMinutes(interval).build());
        assertEquals("0 */" + interval + " * * * *", CronBuilder.everyMinutes(interval).withSeconds().build());
    }

    @Test
    @DisplayName("everyHourAt specific minute")
    void testEveryHourAt() {
        assertEquals("15 * * * *", CronBuilder.everyHourAt(15).build());
        assertEquals("0 15 * * * *", CronBuilder.everyHourAt(15).withSeconds().build());
    }

    @Test
    @DisplayName("dailyAt specific hour and minute")
    void testDailyAt() {
        assertEquals("30 14 * * *", CronBuilder.dailyAt(14, 30).build());
        assertEquals("0 30 14 * * *", CronBuilder.dailyAt(14, 30).withSeconds().build());
    }

    @Test
    @DisplayName("weeklyOn specific DayOfWeek, hour, and minute")
    void testWeeklyOn() {
        assertEquals("0 9 * * 1", CronBuilder.weeklyOn(DayOfWeek.MONDAY, 9, 0).build());
        assertEquals("15 18 * * 5", CronBuilder.weeklyOn(DayOfWeek.FRIDAY, 18, 15).build());
        assertEquals("0 0 9 * * 7", CronBuilder.weeklyOn(DayOfWeek.SUNDAY, 9, 0).withSeconds().build());
    }

    @Test
    @DisplayName("monthlyOn dayOfMonth, hour, minute")
    void testMonthlyOn() {
        assertEquals("0 0 1 * *", CronBuilder.monthlyOn(1, 0, 0).build());
        assertEquals("45 23 31 * *", CronBuilder.monthlyOn(31, 23, 45).build());
        assertEquals("0 0 0 15 * *", CronBuilder.monthlyOn(15, 0, 0).withSeconds().build());
    }

    @Test
    @DisplayName("Chained fluent customization: weekdays, weekends, month, and hour range")
    void testFluentChaining() {
        String weekdayWorkHours = CronBuilder.create()
                .at(9, 0)
                .weekdays()
                .build();
        assertEquals("0 9 * * 1-5", weekdayWorkHours);

        String weekendSchedule = CronBuilder.create()
                .at(12, 0)
                .weekends()
                .build();
        assertEquals("0 12 * * 6,7", weekendSchedule);

        String complexCron = CronBuilder.create()
                .betweenHours(9, 17)
                .minutes("*/10")
                .daysOfWeek(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
                .month(Month.OCTOBER)
                .withSeconds(0)
                .build();
        assertEquals("0 */10 9-17 * 10 1,3,5", complexCron);
    }

    @Test
    @DisplayName("Range and boundary argument validation")
    void testBoundaryValidations() {
        assertThrows(IllegalArgumentException.class, () -> CronBuilder.everyMinutes(0));
        assertThrows(IllegalArgumentException.class, () -> CronBuilder.everyMinutes(60));

        assertThrows(IllegalArgumentException.class, () -> CronBuilder.everyHourAt(-1));
        assertThrows(IllegalArgumentException.class, () -> CronBuilder.everyHourAt(60));

        assertThrows(IllegalArgumentException.class, () -> CronBuilder.dailyAt(24, 0));
        assertThrows(IllegalArgumentException.class, () -> CronBuilder.dailyAt(0, -1));

        assertThrows(IllegalArgumentException.class, () -> CronBuilder.monthlyOn(0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> CronBuilder.monthlyOn(32, 0, 0));

        assertThrows(IllegalArgumentException.class, () -> CronBuilder.create().withSeconds(60));
        assertThrows(IllegalArgumentException.class, () -> CronBuilder.create().betweenHours(18, 9));
    }

    @Test
    @DisplayName("Invalid field characters throw IllegalStateException")
    void testInvalidCharacters() {
        CronBuilder builder = CronBuilder.create().minutes("invalid field with spaces!");
        assertThrows(IllegalStateException.class, builder::build);
    }
}
