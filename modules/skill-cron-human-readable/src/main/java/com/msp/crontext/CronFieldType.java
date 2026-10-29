package com.msp.crontext;

/**
 * Enumeration of standard cron expression fields.
 */
public enum CronFieldType {
    SECOND(0, 59, "초", "second"),
    MINUTE(0, 59, "분", "minute"),
    HOUR(0, 23, "시", "hour"),
    DAY_OF_MONTH(1, 31, "일", "day of month"),
    MONTH(1, 12, "월", "month"),
    DAY_OF_WEEK(0, 7, "요일", "day of week"),
    YEAR(1970, 2099, "년", "year");

    private final int min;
    private final int max;
    private final String koreanName;
    private final String englishName;

    CronFieldType(int min, int max, String koreanName, String englishName) {
        this.min = min;
        this.max = max;
        this.koreanName = koreanName;
        this.englishName = englishName;
    }

    public int min() {
        return min;
    }

    public int max() {
        return max;
    }

    public String koreanName() {
        return koreanName;
    }

    public String englishName() {
        return englishName;
    }
}
