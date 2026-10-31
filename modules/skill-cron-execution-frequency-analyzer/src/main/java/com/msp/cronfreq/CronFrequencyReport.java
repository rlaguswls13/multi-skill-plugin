package com.msp.cronfreq;

import java.util.Arrays;
import java.util.Objects;

/**
 * Report containing execution frequency projections and distribution histograms for a cron expression.
 *
 * @param dailyCount      average daily execution count
 * @param weeklyCount     average weekly execution count
 * @param monthlyCount    average monthly execution count
 * @param yearlyCount     total projected yearly execution count
 * @param hourlyHistogram 24-element array showing execution distribution across hours of day (00 to 23)
 * @param dowHistogram    7-element array showing execution distribution across days of week (0=SUN to 6=SAT)
 */
public record CronFrequencyReport(
        long dailyCount,
        long weeklyCount,
        long monthlyCount,
        long yearlyCount,
        int[] hourlyHistogram,
        int[] dowHistogram
) {
    public CronFrequencyReport {
        Objects.requireNonNull(hourlyHistogram, "hourlyHistogram must not be null");
        Objects.requireNonNull(dowHistogram, "dowHistogram must not be null");
        if (hourlyHistogram.length != 24) {
            throw new IllegalArgumentException("hourlyHistogram length must be exactly 24");
        }
        if (dowHistogram.length != 7) {
            throw new IllegalArgumentException("dowHistogram length must be exactly 7");
        }
        hourlyHistogram = hourlyHistogram.clone();
        dowHistogram = dowHistogram.clone();
    }

    @Override
    public int[] hourlyHistogram() {
        return hourlyHistogram.clone();
    }

    @Override
    public int[] dowHistogram() {
        return dowHistogram.clone();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CronFrequencyReport that)) return false;
        return dailyCount == that.dailyCount &&
                weeklyCount == that.weeklyCount &&
                monthlyCount == that.monthlyCount &&
                yearlyCount == that.yearlyCount &&
                Arrays.equals(hourlyHistogram, that.hourlyHistogram) &&
                Arrays.equals(dowHistogram, that.dowHistogram);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(dailyCount, weeklyCount, monthlyCount, yearlyCount);
        result = 31 * result + Arrays.hashCode(hourlyHistogram);
        result = 31 * result + Arrays.hashCode(dowHistogram);
        return result;
    }
}
