package com.msp.cronlang;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Natural language human schedule parser to standard 5-part cron expressions.
 * Pure Java 21 implementation supporting Korean and English patterns.
 */
public final class NaturalCronParser {

    private static final Map<String, Integer> DAY_OF_WEEK_MAP = Map.ofEntries(
            Map.entry("sunday", 0),
            Map.entry("sun", 0),
            Map.entry("일요일", 0),
            Map.entry("일", 0),

            Map.entry("monday", 1),
            Map.entry("mon", 1),
            Map.entry("월요일", 1),
            Map.entry("월", 1),

            Map.entry("tuesday", 2),
            Map.entry("tue", 2),
            Map.entry("tues", 2),
            Map.entry("화요일", 2),
            Map.entry("화", 2),

            Map.entry("wednesday", 3),
            Map.entry("wed", 3),
            Map.entry("수요일", 3),
            Map.entry("수", 3),

            Map.entry("thursday", 4),
            Map.entry("thu", 4),
            Map.entry("thur", 4),
            Map.entry("thurs", 4),
            Map.entry("목요일", 4),
            Map.entry("목", 4),

            Map.entry("friday", 5),
            Map.entry("fri", 5),
            Map.entry("금요일", 5),
            Map.entry("금", 5),

            Map.entry("saturday", 6),
            Map.entry("sat", 6),
            Map.entry("토요일", 6),
            Map.entry("토", 6)
    );

    // Hourly
    private static final Pattern HOURLY_PATTERN =
            Pattern.compile("^(?:매시간|매\\s*시간|매시간\\s*정각|every\\s+hour|hourly)$");

    // Every N minutes
    private static final Pattern MINUTE_INTERVAL_PATTERN =
            Pattern.compile("^(?:(\\d+)\\s*(?:분마다|분\\s*마다)|매\\s*(\\d+)\\s*분|every\\s+(\\d+)\\s*(?:minutes?|mins?))$");

    // Every N hours
    private static final Pattern HOUR_INTERVAL_PATTERN =
            Pattern.compile("^(?:(\\d+)\\s*(?:시간마다|시간\\s*마다)|매\\s*(\\d+)\\s*시간|every\\s+(\\d+)\\s*(?:hours?|hrs?))$");

    // Daily at HH:mm
    private static final Pattern DAILY_PATTERN =
            Pattern.compile("^(?:매일|매\\s*일|every\\s+day(?:\\s+at)?|daily(?:\\s+at)?)\\s+(\\d{1,2}):(\\d{2})$");

    // Weekday at HH:mm
    private static final Pattern WEEKDAY_PATTERN =
            Pattern.compile("^(?:매주\\s*평일|평일|every\\s+weekday(?:s)?(?:\\s+at)?|weekdays(?:\\s+at)?)\\s+(\\d{1,2}):(\\d{2})$");

    // Weekend at HH:mm
    private static final Pattern WEEKEND_PATTERN =
            Pattern.compile("^(?:매주\\s*주말|주말|every\\s+weekend(?:s)?(?:\\s+at)?|weekends(?:\\s+at)?)\\s+(\\d{1,2}):(\\d{2})$");

    // Weekly day of week at HH:mm (Korean)
    private static final Pattern WEEKLY_KO_PATTERN =
            Pattern.compile("^(?:매주\\s*)?([가-힣]+)(?:마다)?\\s+(\\d{1,2}):(\\d{2})$");

    // Weekly day of week at HH:mm (English)
    private static final Pattern WEEKLY_EN_PATTERN =
            Pattern.compile("^every\\s+([a-z]+)(?:\\s+at)?\\s+(\\d{1,2}):(\\d{2})$");

    // Monthly day of month at HH:mm (Korean)
    private static final Pattern MONTHLY_KO_PATTERN =
            Pattern.compile("^(?:매월|매달)\\s*(\\d{1,2})일(?:\\s*마다)?\\s+(\\d{1,2}):(\\d{2})$");

    // Monthly day of month at HH:mm (English: e.g. "every 1st day of month at 00:00")
    private static final Pattern MONTHLY_EN_PATTERN =
            Pattern.compile("^(?:every\\s+(\\d{1,2})(?:st|nd|rd|th)?\\s+day\\s+of\\s+(?:the\\s+)?month|every\\s+month\\s+on\\s+(?:the\\s+)?(\\d{1,2})(?:st|nd|rd|th)?(?:\\s+day)?|monthly\\s+on\\s+(?:the\\s+)?(\\d{1,2})(?:st|nd|rd|th)?(?:\\s+day)?)(?:\\s+at)?\\s+(\\d{1,2}):(\\d{2})$");

    private NaturalCronParser() {
    }

    /**
     * Parses a natural language sentence into a standard 5-part cron expression.
     *
     * @param naturalLanguage schedule description in Korean or English
     * @return standard cron expression: "minute hour day-of-month month day-of-week"
     * @throws IllegalArgumentException if the expression cannot be parsed or values are invalid
     */
    public static String parse(String naturalLanguage) {
        Objects.requireNonNull(naturalLanguage, "naturalLanguage must not be null");
        String normalized = naturalLanguage.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Schedule sentence cannot be blank");
        }

        // 1. Hourly check
        if (HOURLY_PATTERN.matcher(normalized).matches()) {
            return "0 * * * *";
        }

        // 2. Minute intervals ("15분마다", "every 15 minutes")
        Matcher mMin = MINUTE_INTERVAL_PATTERN.matcher(normalized);
        if (mMin.matches()) {
            String val = firstNonNull(mMin.group(1), mMin.group(2), mMin.group(3));
            int mins = Integer.parseInt(val);
            if (mins <= 0 || mins > 59) {
                throw new IllegalArgumentException("Minute interval must be between 1 and 59: " + mins);
            }
            return "*/" + mins + " * * * *";
        }

        // 3. Hour intervals ("2시간마다", "every 3 hours")
        Matcher mHour = HOUR_INTERVAL_PATTERN.matcher(normalized);
        if (mHour.matches()) {
            String val = firstNonNull(mHour.group(1), mHour.group(2), mHour.group(3));
            int hours = Integer.parseInt(val);
            if (hours <= 0 || hours > 23) {
                throw new IllegalArgumentException("Hour interval must be between 1 and 23: " + hours);
            }
            return "0 */" + hours + " * * *";
        }

        // 4. Weekday ("매주 평일 08:00", "every weekday at 08:00")
        Matcher mWeekday = WEEKDAY_PATTERN.matcher(normalized);
        if (mWeekday.matches()) {
            int hour = validateHour(mWeekday.group(1));
            int minute = validateMinute(mWeekday.group(2));
            return minute + " " + hour + " * * 1-5";
        }

        // 5. Weekend ("매주 주말 10:00", "every weekend at 10:00")
        Matcher mWeekend = WEEKEND_PATTERN.matcher(normalized);
        if (mWeekend.matches()) {
            int hour = validateHour(mWeekend.group(1));
            int minute = validateMinute(mWeekend.group(2));
            return minute + " " + hour + " * * 0,6";
        }

        // 6. Monthly Korean ("매월 1일 00:00")
        Matcher mMonKo = MONTHLY_KO_PATTERN.matcher(normalized);
        if (mMonKo.matches()) {
            int dom = validateDayOfMonth(mMonKo.group(1));
            int hour = validateHour(mMonKo.group(2));
            int minute = validateMinute(mMonKo.group(3));
            return minute + " " + hour + " " + dom + " * *";
        }

        // 7. Monthly English ("every 1st day of month at 00:00")
        Matcher mMonEn = MONTHLY_EN_PATTERN.matcher(normalized);
        if (mMonEn.matches()) {
            String domStr = firstNonNull(mMonEn.group(1), mMonEn.group(2), mMonEn.group(3));
            int dom = validateDayOfMonth(domStr);
            int hour = validateHour(mMonEn.group(4));
            int minute = validateMinute(mMonEn.group(5));
            return minute + " " + hour + " " + dom + " * *";
        }

        // 8. Weekly English ("every monday at 14:30")
        Matcher mWeekEn = WEEKLY_EN_PATTERN.matcher(normalized);
        if (mWeekEn.matches()) {
            String dayName = mWeekEn.group(1);
            Integer dow = DAY_OF_WEEK_MAP.get(dayName);
            if (dow != null) {
                int hour = validateHour(mWeekEn.group(2));
                int minute = validateMinute(mWeekEn.group(3));
                return minute + " " + hour + " * * " + dow;
            }
        }

        // 9. Weekly Korean ("매주 월요일 14:30")
        Matcher mWeekKo = WEEKLY_KO_PATTERN.matcher(normalized);
        if (mWeekKo.matches()) {
            String dayName = mWeekKo.group(1);
            Integer dow = DAY_OF_WEEK_MAP.get(dayName);
            if (dow != null) {
                int hour = validateHour(mWeekKo.group(2));
                int minute = validateMinute(mWeekKo.group(3));
                return minute + " " + hour + " * * " + dow;
            }
        }

        // 10. Daily ("매일 09:00", "every day at 09:00")
        Matcher mDaily = DAILY_PATTERN.matcher(normalized);
        if (mDaily.matches()) {
            int hour = validateHour(mDaily.group(1));
            int minute = validateMinute(mDaily.group(2));
            return minute + " " + hour + " * * *";
        }

        // Special aliases
        if (normalized.equals("자정") || normalized.equals("매일 자정") || normalized.equals("midnight") || normalized.equals("every day at midnight")) {
            return "0 0 * * *";
        }
        if (normalized.equals("정오") || normalized.equals("매일 정오") || normalized.equals("noon") || normalized.equals("every day at noon")) {
            return "0 12 * * *";
        }

        throw new IllegalArgumentException("Cannot parse natural schedule sentence: \"" + naturalLanguage + "\"");
    }

    /**
     * Alias for {@link #parse(String)}.
     */
    public static String toCron(String naturalLanguage) {
        return parse(naturalLanguage);
    }

    private static String firstNonNull(String... items) {
        for (String item : items) {
            if (item != null) {
                return item;
            }
        }
        throw new IllegalArgumentException("No match found in groups");
    }

    private static int validateHour(String s) {
        int h = Integer.parseInt(s);
        if (h < 0 || h > 23) {
            throw new IllegalArgumentException("Hour must be between 0 and 23: " + h);
        }
        return h;
    }

    private static int validateMinute(String s) {
        int m = Integer.parseInt(s);
        if (m < 0 || m > 59) {
            throw new IllegalArgumentException("Minute must be between 0 and 59: " + m);
        }
        return m;
    }

    private static int validateDayOfMonth(String s) {
        int d = Integer.parseInt(s);
        if (d < 1 || d > 31) {
            throw new IllegalArgumentException("Day of month must be between 1 and 31: " + d);
        }
        return d;
    }
}
