package com.msp.crontext;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class CronHumanReadableTest {

    @Test
    @DisplayName("5-field common expressions in Korean (default)")
    void testStandardFiveFieldKorean() {
        assertEquals("매 분", CronHumanReadable.describe("* * * * *"));
        assertEquals("매 시 정각", CronHumanReadable.describe("0 * * * *"));
        assertEquals("15분마다", CronHumanReadable.describe("*/15 * * * *"));
        assertEquals("매일 00:00", CronHumanReadable.describe("0 0 * * *"));
        assertEquals("매일 09:30", CronHumanReadable.describe("30 9 * * *"));
        assertEquals("매월 1일 00:00", CronHumanReadable.describe("0 0 1 * *"));
        assertEquals("매월 말일 00:00", CronHumanReadable.describe("0 0 L * *"));
        assertEquals("매월 마지막 평일 00:00", CronHumanReadable.describe("0 0 LW * *"));
        assertEquals("매월 15일에서 가장 가까운 평일 00:00", CronHumanReadable.describe("0 0 15W * *"));
        assertEquals("매년 1월 1일 00:00", CronHumanReadable.describe("0 0 1 1 *"));
        assertEquals("매주 월요일 00:00", CronHumanReadable.describe("0 0 * * 1"));
    }

    @Test
    @DisplayName("5-field common expressions in English")
    void testStandardFiveFieldEnglish() {
        assertEquals("Every minute", CronHumanReadable.describe("* * * * *", Locale.ENGLISH));
        assertEquals("Every hour on the hour", CronHumanReadable.describe("0 * * * *", Locale.ENGLISH));
        assertEquals("Every 15 minutes", CronHumanReadable.describe("*/15 * * * *", Locale.ENGLISH));
        assertEquals("Every day at 00:00", CronHumanReadable.describe("0 0 * * *", Locale.ENGLISH));
        assertEquals("Every day at 09:30", CronHumanReadable.describe("30 9 * * *", Locale.ENGLISH));
        assertEquals("On day 1 of the month at 00:00", CronHumanReadable.describe("0 0 1 * *", Locale.ENGLISH));
        assertEquals("On the last day of the month at 00:00", CronHumanReadable.describe("0 0 L * *", Locale.ENGLISH));
        assertEquals("On the last weekday of the month at 00:00", CronHumanReadable.describe("0 0 LW * *", Locale.ENGLISH));
        assertEquals("On the nearest weekday to day 15 of the month at 00:00", CronHumanReadable.describe("0 0 15W * *", Locale.ENGLISH));
        assertEquals("On day 1 of January at 00:00", CronHumanReadable.describe("0 0 1 1 *", Locale.ENGLISH));
        assertEquals("Every Monday at 00:00", CronHumanReadable.describe("0 0 * * 1", Locale.ENGLISH));
    }

    @Test
    @DisplayName("6-field expressions with seconds")
    void testSixFieldSeconds() {
        assertEquals("매초", CronHumanReadable.describe("* * * * * *"));
        assertEquals("Every second", CronHumanReadable.describe("* * * * * *", Locale.ENGLISH));

        assertEquals("10초마다", CronHumanReadable.describe("*/10 * * * * *"));
        assertEquals("Every 10 seconds", CronHumanReadable.describe("*/10 * * * * *", Locale.ENGLISH));

        assertEquals("매일 09:30:00", CronHumanReadable.describe("0 30 9 * * ?"));
        assertEquals("Every day at 09:30:00", CronHumanReadable.describe("0 30 9 * * ?", Locale.ENGLISH));

        assertEquals("매주 수요일 12:00:00", CronHumanReadable.describe("0 0 12 ? * WED"));
        assertEquals("Every Wednesday at 12:00:00", CronHumanReadable.describe("0 0 12 ? * WED", Locale.ENGLISH));

        assertEquals("매월 마지막 금요일 00:00:00", CronHumanReadable.describe("0 0 0 ? * 5L"));
        assertEquals("On the last Friday of the month at 00:00:00", CronHumanReadable.describe("0 0 0 ? * 5L", Locale.ENGLISH));
    }

    @Test
    @DisplayName("Range expressions")
    void testRanges() {
        String descKo = CronHumanReadable.describe("0 9-18 * * 1-5");
        assertTrue(descKo.contains("월요일부터 금요일까지") && descKo.contains("09:00부터 18:00까지 매 시 정각"));

        String descEn = CronHumanReadable.describe("0 9-18 * * 1-5", Locale.ENGLISH);
        assertEquals("Every hour from 09:00 to 18:00, Monday through Friday", descEn);
    }

    @Test
    @DisplayName("Multiple list values")
    void testListValues() {
        String descKo = CronHumanReadable.describe("0 0 1,15 * *");
        assertEquals("매월 1일, 15일 00:00", descKo);

        String descEn = CronHumanReadable.describe("0 0 1,15 * *", Locale.ENGLISH);
        assertEquals("On day 1 and 15 of the month at 00:00", descEn);
    }

    @Test
    @DisplayName("Year support in 7-field cron")
    void testSevenFieldYear() {
        String descKo = CronHumanReadable.describe("0 0 12 1 1 ? 2026");
        assertTrue(descKo.contains("2026년") && descKo.contains("1월") && descKo.contains("1일") && descKo.contains("12:00:00"));

        String descEn = CronHumanReadable.describe("0 0 12 1 1 ? 2026", Locale.ENGLISH);
        assertTrue(descEn.contains("in year 2026") && descEn.contains("January") && descEn.contains("12:00:00"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "0 0", "0 0 * * * * * *"})
    @DisplayName("Invalid field counts throw IllegalArgumentException")
    void testInvalidCronSyntax(String invalid) {
        assertThrows(IllegalArgumentException.class, () -> CronHumanReadable.describe(invalid));
    }

    @Test
    @DisplayName("Null cron expression throws IllegalArgumentException")
    void testNullCron() {
        assertThrows(IllegalArgumentException.class, () -> CronHumanReadable.describe(null));
    }
}
