package com.msp.cronlang;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CronNaturalLanguageParserTest {

    @Test
    @DisplayName("Korean: 매월 마지막 금요일 오후 6시 -> 0 18 * * 5L / 0 0 18 ? * 6L")
    void testKoreanMonthlyLastFriday() {
        ParsedCronResult res = CronNaturalLanguageParser.parse("매월 마지막 금요일 오후 6시");
        assertEquals("0 18 * * 5L", res.standardExpression());
        assertEquals("0 0 18 ? * 6L", res.quartzExpression());
        assertTrue(res.humanDescription().contains("마지막 금요일"));
    }

    @Test
    @DisplayName("Korean: 매주 월수금 오전 9시 30분 -> 30 9 * * 1,3,5 / 0 30 9 ? * MON,WED,FRI")
    void testKoreanDaysOfWeek() {
        ParsedCronResult res = CronNaturalLanguageParser.parse("매주 월수금 오전 9시 30분");
        assertEquals("30 9 * * 1,3,5", res.standardExpression());
        assertEquals("0 30 9 ? * MON,WED,FRI", res.quartzExpression());
    }

    @Test
    @DisplayName("Korean: 평일(월-금) 15분마다 -> */15 * * * 1-5 / 0 */15 * ? * MON-FRI")
    void testKoreanWeekdayInterval() {
        ParsedCronResult res = CronNaturalLanguageParser.parse("평일(월-금) 15분마다");
        assertEquals("*/15 * * * 1-5", res.standardExpression());
        assertEquals("0 */15 * ? * MON-FRI", res.quartzExpression());

        ParsedCronResult res2 = CronNaturalLanguageParser.parse("평일 15분마다");
        assertEquals("*/15 * * * 1-5", res2.standardExpression());
    }

    @Test
    @DisplayName("Korean: 매일 밤 11시 -> 0 23 * * * / 0 0 23 * * ?")
    void testKoreanDailyNight() {
        ParsedCronResult res = CronNaturalLanguageParser.parse("매일 밤 11시");
        assertEquals("0 23 * * *", res.standardExpression());
        assertEquals("0 0 23 * * ?", res.quartzExpression());
    }

    @Test
    @DisplayName("Korean: 10분마다 -> */10 * * * *")
    void testKoreanInterval() {
        ParsedCronResult res = CronNaturalLanguageParser.parse("10분마다");
        assertEquals("*/10 * * * *", res.standardExpression());
        assertEquals("0 */10 * * * ?", res.quartzExpression());
    }

    @Test
    @DisplayName("Korean: 매시 30분 -> 30 * * * *")
    void testKoreanHourlyAtMinute() {
        ParsedCronResult res = CronNaturalLanguageParser.parse("매시 30분");
        assertEquals("30 * * * *", res.standardExpression());
        assertEquals("0 30 * * * ?", res.quartzExpression());
    }

    @Test
    @DisplayName("Korean: 매월 둘째주 화요일 오후 3시 30분 -> 30 15 * * 2#2 / 0 30 15 ? * 3#2")
    void testKoreanMonthlyNthWeekday() {
        ParsedCronResult res = CronNaturalLanguageParser.parse("매월 둘째주 화요일 오후 3시 30분");
        assertEquals("30 15 * * 2#2", res.standardExpression());
        assertEquals("0 30 15 ? * 3#2", res.quartzExpression());
    }

    @Test
    @DisplayName("Korean: 매월 15일 오후 3시 -> 0 15 15 * *")
    void testKoreanMonthlyOnDay() {
        ParsedCronResult res = CronNaturalLanguageParser.parse("매월 15일 오후 3시");
        assertEquals("0 15 15 * *", res.standardExpression());
        assertEquals("0 0 15 15 * ?", res.quartzExpression());
    }

    @Test
    @DisplayName("English: every 2nd Tuesday at 3:30pm -> 30 15 * * 2#2 / 0 30 15 ? * 3#2")
    void testEnglishNthWeekday() {
        ParsedCronResult res = CronNaturalLanguageParser.parse("every 2nd Tuesday at 3:30pm");
        assertEquals("30 15 * * 2#2", res.standardExpression());
        assertEquals("0 30 15 ? * 3#2", res.quartzExpression());
    }

    @Test
    @DisplayName("English: every 15 minutes between 9am and 5pm on weekdays")
    void testEnglishActiveHoursWeekdays() {
        ParsedCronResult res = CronNaturalLanguageParser.parse("every 15 minutes between 9am and 5pm on weekdays");
        assertEquals("*/15 9-17 * * 1-5", res.standardExpression());
        assertEquals("0 */15 9-17 ? * MON-FRI", res.quartzExpression());
    }

    @Test
    @DisplayName("English: every day at midnight -> 0 0 * * *")
    void testEnglishMidnight() {
        ParsedCronResult res = CronNaturalLanguageParser.parse("every day at midnight");
        assertEquals("0 0 * * *", res.standardExpression());
        assertEquals("0 0 0 * * ?", res.quartzExpression());
    }

    @Test
    @DisplayName("English: every last Friday of the month at 6pm")
    void testEnglishLastWeekdayOfMonth() {
        ParsedCronResult res = CronNaturalLanguageParser.parse("every last Friday of the month at 6pm");
        assertEquals("0 18 * * 5L", res.standardExpression());
        assertEquals("0 0 18 ? * 6L", res.quartzExpression());
    }

    @Test
    @DisplayName("English: at 14:00 on the 1st of every month")
    void testEnglishFirstOfMonth() {
        ParsedCronResult res = CronNaturalLanguageParser.parse("at 14:00 on the 1st of every month");
        assertEquals("0 14 1 * *", res.standardExpression());
        assertEquals("0 0 14 1 * ?", res.quartzExpression());
    }

    @Test
    @DisplayName("English: every Monday at 8am")
    void testEnglishWeekly() {
        ParsedCronResult res = CronNaturalLanguageParser.parse("every Monday at 8am");
        assertEquals("0 8 * * 1", res.standardExpression());
        assertEquals("0 0 8 ? * MON", res.quartzExpression());
    }

    @Test
    @DisplayName("Invalid or unrecognized expression throws IllegalArgumentException")
    void testInvalidExpressions() {
        assertThrows(IllegalArgumentException.class, () -> CronNaturalLanguageParser.parse("random gibberish text"));
        assertThrows(IllegalArgumentException.class, () -> CronNaturalLanguageParser.parse(""));
        assertTrue(CronNaturalLanguageParser.tryParse("unsupported expression").isEmpty());
    }
}
