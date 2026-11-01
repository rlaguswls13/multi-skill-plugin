package com.msp.crondesc;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CronDescriberTest {

    @Test
    @DisplayName("Every minute expression translation for Korean and English")
    void testEveryMinute() {
        assertEquals("매 분마다", CronDescriber.describeKorean("* * * * *"));
        assertEquals("Every minute", CronDescriber.describeEnglish("* * * * *"));
    }

    @Test
    @DisplayName("Step minute expression translation for Korean and English")
    void testStepMinute() {
        assertEquals("매 5분마다", CronDescriber.describeKorean("*/5 * * * *"));
        assertEquals("Every 5 minutes", CronDescriber.describeEnglish("*/5 * * * *"));

        assertEquals("매 15분마다", CronDescriber.describeKorean("*/15 * * * *"));
        assertEquals("Every 15 minutes", CronDescriber.describeEnglish("*/15 * * * *"));
    }

    @Test
    @DisplayName("Step hour expression translation for Korean and English")
    void testStepHour() {
        assertEquals("매 2시간마다", CronDescriber.describeKorean("0 */2 * * *"));
        assertEquals("Every 2 hours", CronDescriber.describeEnglish("0 */2 * * *"));
    }

    @Test
    @DisplayName("Specific time expression translation for Korean and English")
    void testSpecificTime() {
        assertEquals("매일 오후 2시 30분마다", CronDescriber.describeKorean("30 14 * * *"));
        assertEquals("Every day at 14:30", CronDescriber.describeEnglish("30 14 * * *"));
    }

    @Test
    @DisplayName("Weekday range expression translation for Korean and English")
    void testWeekdayRange() {
        assertEquals("매주 월요일부터 금요일까지 오전 9시 00분마다", CronDescriber.describeKorean("0 9 * * 1-5"));
        assertEquals("Every Monday through Friday at 09:00", CronDescriber.describeEnglish("0 9 * * 1-5"));
    }

    @Test
    @DisplayName("Specific month and day expression translation for Korean and English")
    void testSpecificMonthAndDay() {
        assertEquals("매년 1월 1일 오전 12시 00분마다", CronDescriber.describeKorean("0 0 1 1 *"));
        assertEquals("Every January 1 at 00:00", CronDescriber.describeEnglish("0 0 1 1 *"));
    }

    @Test
    @DisplayName("Day of month without month specified")
    void testDayOfMonth() {
        assertEquals("매월 15일 오전 9시 30분마다", CronDescriber.describeKorean("30 9 15 * *"));
        assertEquals("Every month on day 15 at 09:30", CronDescriber.describeEnglish("30 9 15 * *"));
    }

    @Test
    @DisplayName("6-field expressions with seconds")
    void testSixFieldSeconds() {
        assertEquals("매 초마다", CronDescriber.describeKorean("* * * * * *"));
        assertEquals("Every second", CronDescriber.describeEnglish("* * * * * *"));

        assertEquals("매 10초마다", CronDescriber.describeKorean("*/10 * * * * *"));
        assertEquals("Every 10 seconds", CronDescriber.describeEnglish("*/10 * * * * *"));

        assertEquals("매일 오후 2시 30분 45초마다", CronDescriber.describeKorean("45 30 14 * * *"));
        assertEquals("Every day at 14:30:45", CronDescriber.describeEnglish("45 30 14 * * *"));
    }

    @Test
    @DisplayName("Invalid cron expressions throw IllegalArgumentException")
    void testInvalidExpressions() {
        assertThrows(IllegalArgumentException.class, () -> CronDescriber.describeKorean(null));
        assertThrows(IllegalArgumentException.class, () -> CronDescriber.describeKorean(""));
        assertThrows(IllegalArgumentException.class, () -> CronDescriber.describeKorean("* * *")); // Too few fields
        assertThrows(IllegalArgumentException.class, () -> CronDescriber.describeKorean("* * * * * * *")); // Too many fields
        assertThrows(IllegalArgumentException.class, () -> CronDescriber.describeKorean("65 * * * *")); // Minute out of range
        assertThrows(IllegalArgumentException.class, () -> CronDescriber.describeKorean("0 25 * * *")); // Hour out of range
    }
}
