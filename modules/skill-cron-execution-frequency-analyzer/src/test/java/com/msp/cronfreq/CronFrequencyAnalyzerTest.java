package com.msp.cronfreq;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CronFrequencyAnalyzer Tests")
class CronFrequencyAnalyzerTest {

    @Test
    @DisplayName("Analyze daily midnight cron '0 0 * * *'")
    void testDailyMidnightCron() {
        CronFrequencyReport report = CronFrequencyAnalyzer.analyze("0 0 * * *");

        assertEquals(365, report.yearlyCount());
        assertEquals(1, report.dailyCount());
        assertEquals(7, report.weeklyCount());
        assertEquals(30, report.monthlyCount());

        int[] hourly = report.hourlyHistogram();
        assertEquals(365, hourly[0]);
        for (int h = 1; h < 24; h++) {
            assertEquals(0, hourly[h], "Hour " + h + " should have 0 triggers");
        }

        assertEquals(0, CronFrequencyAnalyzer.getPeakHour("0 0 * * *"));
    }

    @Test
    @DisplayName("Analyze every minute cron '* * * * *'")
    void testEveryMinuteCron() {
        CronFrequencyReport report = CronFrequencyAnalyzer.analyze("* * * * *");

        // 365 days * 24 hours * 60 minutes = 525,600
        assertEquals(525600, report.yearlyCount());
        assertEquals(1440, report.dailyCount());
        assertEquals(10080, report.weeklyCount());

        int[] hourly = report.hourlyHistogram();
        for (int h = 0; h < 24; h++) {
            assertEquals(365 * 60, hourly[h]);
        }
    }

    @Test
    @DisplayName("Analyze business hours weekday cron '0 9-17 * * MON-FRI'")
    void testBusinessHoursWeekdayCron() {
        CronFrequencyReport report = CronFrequencyAnalyzer.analyze("0 9-17 * * MON-FRI");

        int[] dow = report.dowHistogram();
        // 0=SUN, 6=SAT should have 0 triggers
        assertEquals(0, dow[0], "Sunday should have 0 triggers");
        assertEquals(0, dow[6], "Saturday should have 0 triggers");
        for (int d = 1; d <= 5; d++) {
            assertTrue(dow[d] > 0, "Weekday " + d + " should have positive triggers");
        }

        int[] hourly = report.hourlyHistogram();
        for (int h = 0; h < 9; h++) {
            assertEquals(0, hourly[h]);
        }
        for (int h = 9; h <= 17; h++) {
            assertTrue(hourly[h] > 0);
        }
        for (int h = 18; h < 24; h++) {
            assertEquals(0, hourly[h]);
        }
    }

    @Test
    @DisplayName("Analyze macro expressions (@daily, @hourly, @monthly)")
    void testMacros() {
        CronFrequencyReport dailyReport = CronFrequencyAnalyzer.analyze("@daily");
        assertEquals(365, dailyReport.yearlyCount());

        CronFrequencyReport hourlyReport = CronFrequencyAnalyzer.analyze("@hourly");
        assertEquals(365 * 24, hourlyReport.yearlyCount());

        CronFrequencyReport monthlyReport = CronFrequencyAnalyzer.analyze("@monthly");
        assertEquals(12, monthlyReport.yearlyCount());
    }

    @Test
    @DisplayName("toAsciiHourlyChart formats a readable chart")
    void testAsciiChart() {
        String chart = CronFrequencyAnalyzer.toAsciiHourlyChart("0 8,12,18 * * *");
        assertNotNull(chart);
        assertTrue(chart.contains("Hourly Trigger Distribution"));
        assertTrue(chart.contains("08:00"));
        assertTrue(chart.contains("12:00"));
        assertTrue(chart.contains("18:00"));
        assertTrue(chart.contains("█"));
    }

    @Test
    @DisplayName("CronFrequencyReport defensive copying and validations")
    void testReportImmutability() {
        int[] hourly = new int[24];
        int[] dow = new int[7];
        hourly[10] = 50;

        CronFrequencyReport report = new CronFrequencyReport(10, 70, 300, 3650, hourly, dow);

        // Modifying local array must not alter report
        hourly[10] = 999;
        assertEquals(50, report.hourlyHistogram()[10]);

        // Modifying returned array must not alter report
        int[] returnedHourly = report.hourlyHistogram();
        returnedHourly[10] = 777;
        assertEquals(50, report.hourlyHistogram()[10]);

        assertThrows(IllegalArgumentException.class, () ->
                new CronFrequencyReport(1, 1, 1, 1, new int[23], dow)
        );
    }

    @Test
    @DisplayName("Invalid cron syntax throws IllegalArgumentException")
    void testInvalidSyntax() {
        assertThrows(NullPointerException.class, () -> CronFrequencyAnalyzer.analyze(null));
        assertThrows(IllegalArgumentException.class, () -> CronFrequencyAnalyzer.analyze("invalid cron string"));
        assertThrows(IllegalArgumentException.class, () -> CronFrequencyAnalyzer.analyze("60 * * * *")); // minute > 59
        assertThrows(IllegalArgumentException.class, () -> CronFrequencyAnalyzer.analyze("* 25 * * *")); // hour > 23
        assertThrows(IllegalArgumentException.class, () -> CronFrequencyAnalyzer.analyze("* * 32 * *")); // dom > 31
    }
}
