package com.msp.crondiff;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CronDiffComparatorTest {

    @Test
    @DisplayName("동일한 Cron 비교 시 일치율 100% 검증")
    void testIdenticalCrons() {
        String cron = "0 9 * * 1-5"; // Every weekday at 09:00
        LocalDateTime start = LocalDateTime.of(2026, 11, 2, 0, 0); // Monday
        LocalDateTime end = LocalDateTime.of(2026, 11, 8, 23, 59);  // Sunday

        CronDiffResult result = CronDiffComparator.compare(cron, cron, start, end);
        assertEquals(100.0, result.overlapPercentage());
        assertEquals(5, result.bothMatchTimes().size()); // Mon-Fri (5 days)
        assertTrue(result.onlyAMatchTimes().isEmpty());
        assertTrue(result.onlyBMatchTimes().isEmpty());
    }

    @Test
    @DisplayName("배수 주기 비교 (매 10분 vs 매 5분): overlap 50.0%")
    void testStepOverlap() {
        String cronA = "*/10 * * * *"; // Every 10 mins: 0, 10, 20, 30, 40, 50
        String cronB = "*/5 * * * *";  // Every 5 mins: 0, 5, 10, 15, 20, 25...

        LocalDateTime start = LocalDateTime.of(2026, 11, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 11, 1, 0, 59); // 1 hour

        CronDiffResult result = CronDiffComparator.compare(cronA, cronB, start, end);
        assertEquals(6, result.bothMatchTimes().size());
        assertEquals(0, result.onlyAMatchTimes().size());
        assertEquals(6, result.onlyBMatchTimes().size());
        assertEquals(50.0, result.overlapPercentage()); // 6 / 12 = 50.0%
    }

    @Test
    @DisplayName("전혀 겹치지 않는 Cron 비교 (오전 9시 vs 오후 6시): overlap 0.0%")
    void testMutuallyExclusiveCrons() {
        String cronA = "0 9 * * *";
        String cronB = "0 18 * * *";

        LocalDateTime start = LocalDateTime.of(2026, 11, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 11, 3, 23, 59); // 3 days

        CronDiffResult result = CronDiffComparator.compare(cronA, cronB, start, end);
        assertEquals(0.0, result.overlapPercentage());
        assertTrue(result.bothMatchTimes().isEmpty());
        assertEquals(3, result.onlyAMatchTimes().size());
        assertEquals(3, result.onlyBMatchTimes().size());
    }

    @Test
    @DisplayName("평일 vs 주말 분리 검증")
    void testWeekdayVsWeekend() {
        String weekday = "0 12 * * 1-5";
        String weekend = "0 12 * * 6,0";

        LocalDateTime start = LocalDateTime.of(2026, 11, 2, 0, 0); // Monday
        LocalDateTime end = LocalDateTime.of(2026, 11, 8, 23, 59);  // Sunday

        CronDiffResult result = CronDiffComparator.compare(weekday, weekend, start, end);
        assertEquals(0.0, result.overlapPercentage());
        assertEquals(5, result.onlyAMatchTimes().size()); // Mon, Tue, Wed, Thu, Fri
        assertEquals(2, result.onlyBMatchTimes().size()); // Sat, Sun
        assertTrue(result.bothMatchTimes().isEmpty());
    }

    @Test
    @DisplayName("compareNextOccurrences 차기 N회차 이벤트 비교 검증")
    void testCompareNextOccurrences() {
        String cronA = "0 10 * * *"; // 10:00 every day
        String cronB = "0 10,14 * * *"; // 10:00 and 14:00 every day

        LocalDateTime start = LocalDateTime.of(2026, 11, 1, 0, 0);
        CronDiffResult result = CronDiffComparator.compareNextOccurrences(cronA, cronB, start, 4);

        // 4개 이벤트: 11/1 10:00(both), 11/1 14:00(B), 11/2 10:00(both), 11/2 14:00(B)
        assertEquals(2, result.bothMatchTimes().size());
        assertEquals(0, result.onlyAMatchTimes().size());
        assertEquals(2, result.onlyBMatchTimes().size());
        assertEquals(50.0, result.overlapPercentage());
    }

    @Test
    @DisplayName("summary 보고서 포맷 검증")
    void testSummaryFormat() {
        String cronA = "0 0 * * *";
        String cronB = "0 0 * * *";
        LocalDateTime start = LocalDateTime.of(2026, 11, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 11, 2, 23, 59);

        CronDiffResult result = CronDiffComparator.compare(cronA, cronB, start, end);
        String summary = result.summary();

        assertTrue(summary.contains("[Cron Diff Comparison]"));
        assertTrue(summary.contains("Overlap Ratio: 100.0%"));
        assertTrue(summary.contains("Simultaneous Matches (Overlap): 2"));
    }

    @Test
    @DisplayName("잘못된 Cron 문법 및 경계값 예외 처리 검증")
    void testInvalidCronSyntax() {
        // 필드 개수 불일치
        assertThrows(IllegalArgumentException.class, () -> CronExpression.parse("* * * *"));
        assertThrows(IllegalArgumentException.class, () -> CronExpression.parse("* * * * * *"));

        // 유효 범위를 벗어난 값
        assertThrows(IllegalArgumentException.class, () -> CronExpression.parse("60 * * * *"));
        assertThrows(IllegalArgumentException.class, () -> CronExpression.parse("* 25 * * *"));
        assertThrows(IllegalArgumentException.class, () -> CronExpression.parse("* * 32 * *"));

        // 잘못된 시작/종료 시간
        LocalDateTime now = LocalDateTime.now();
        assertThrows(IllegalArgumentException.class, () ->
                CronDiffComparator.compare("* * * * *", "* * * * *", now, now.minusDays(1)));
    }
}
