package com.msp.crondesc;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Standard 5-field and 6-field (with seconds) Cron expression describer
 * generating fluent Korean and English natural language descriptions.
 */
public final class CronDescriber {

    private static final String[] EN_MONTHS = {
            "", "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
    };

    private static final String[] EN_DAYS = {
            "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"
    };

    private static final String[] KO_DAYS = {
            "일요일", "월요일", "화요일", "수요일", "목요일", "금요일", "토요일"
    };

    private CronDescriber() {
        // Utility class
    }

    private record CronFields(
            String second,
            String minute,
            String hour,
            String dayOfMonth,
            String month,
            String dayOfWeek,
            boolean hasSeconds
    ) {}

    /**
     * Translates a standard 5-field or 6-field cron expression into a natural Korean description.
     *
     * @param cronExpression standard cron expression string
     * @return natural Korean description
     * @throws IllegalArgumentException if the expression is invalid
     */
    public static String describeKorean(String cronExpression) {
        CronFields fields = parseAndValidate(cronExpression);

        // 1. Seconds level intervals
        if (fields.hasSeconds) {
            if ("*".equals(fields.second) && "*".equals(fields.minute) && "*".equals(fields.hour)) {
                return "매 초마다";
            }
            if (fields.second.startsWith("*/") && "*".equals(fields.minute) && "*".equals(fields.hour)) {
                int secStep = parseStep(fields.second, 1, 59);
                return "매 " + secStep + "초마다";
            }
        }

        // 2. Minute level intervals
        if (isWildcardOrZero(fields.second, fields.hasSeconds)) {
            if ("*".equals(fields.minute) && "*".equals(fields.hour) && isWildcard(fields.dayOfMonth) && isWildcard(fields.month) && isWildcard(fields.dayOfWeek)) {
                return "매 분마다";
            }
            if (fields.minute.startsWith("*/") && "*".equals(fields.hour) && isWildcard(fields.dayOfMonth) && isWildcard(fields.month) && isWildcard(fields.dayOfWeek)) {
                int minStep = parseStep(fields.minute, 1, 59);
                return "매 " + minStep + "분마다";
            }
        }

        // 3. Hour level intervals
        if (isWildcardOrZero(fields.second, fields.hasSeconds) && isZero(fields.minute)) {
            if ("*".equals(fields.hour) && isWildcard(fields.dayOfMonth) && isWildcard(fields.month) && isWildcard(fields.dayOfWeek)) {
                return "매 시간마다";
            }
            if (fields.hour.startsWith("*/") && isWildcard(fields.dayOfMonth) && isWildcard(fields.month) && isWildcard(fields.dayOfWeek)) {
                int hourStep = parseStep(fields.hour, 1, 23);
                return "매 " + hourStep + "시간마다";
            }
        }

        // 4. Fixed Time Execution
        int hour = parseSingleInt(fields.hour, 0, 23, "hour");
        int minute = parseSingleInt(fields.minute, 0, 59, "minute");
        int second = fields.hasSeconds ? parseSingleInt(fields.second, 0, 59, "second") : 0;

        String timeStr = formatKoreanTime(hour, minute, second);

        // 4a. Day of Week specified
        if (!isWildcard(fields.dayOfWeek)) {
            String dowStr = formatKoreanDayOfWeek(fields.dayOfWeek);
            return "매주 " + dowStr + " " + timeStr;
        }

        // 4b. Month and Day of Month specified
        if (!isWildcard(fields.month) && !isWildcard(fields.dayOfMonth)) {
            int m = parseMonth(fields.month);
            int dom = parseSingleInt(fields.dayOfMonth, 1, 31, "day-of-month");
            return "매년 " + m + "월 " + dom + "일 " + timeStr;
        }

        // 4c. Day of Month specified
        if (!isWildcard(fields.dayOfMonth)) {
            int dom = parseSingleInt(fields.dayOfMonth, 1, 31, "day-of-month");
            return "매월 " + dom + "일 " + timeStr;
        }

        // 4d. Default: Every day at time
        return "매일 " + timeStr;
    }

    /**
     * Translates a standard 5-field or 6-field cron expression into a natural English description.
     *
     * @param cronExpression standard cron expression string
     * @return natural English description
     * @throws IllegalArgumentException if the expression is invalid
     */
    public static String describeEnglish(String cronExpression) {
        CronFields fields = parseAndValidate(cronExpression);

        // 1. Seconds level intervals
        if (fields.hasSeconds) {
            if ("*".equals(fields.second) && "*".equals(fields.minute) && "*".equals(fields.hour)) {
                return "Every second";
            }
            if (fields.second.startsWith("*/") && "*".equals(fields.minute) && "*".equals(fields.hour)) {
                int secStep = parseStep(fields.second, 1, 59);
                return "Every " + secStep + " seconds";
            }
        }

        // 2. Minute level intervals
        if (isWildcardOrZero(fields.second, fields.hasSeconds)) {
            if ("*".equals(fields.minute) && "*".equals(fields.hour) && isWildcard(fields.dayOfMonth) && isWildcard(fields.month) && isWildcard(fields.dayOfWeek)) {
                return "Every minute";
            }
            if (fields.minute.startsWith("*/") && "*".equals(fields.hour) && isWildcard(fields.dayOfMonth) && isWildcard(fields.month) && isWildcard(fields.dayOfWeek)) {
                int minStep = parseStep(fields.minute, 1, 59);
                return "Every " + minStep + " minutes";
            }
        }

        // 3. Hour level intervals
        if (isWildcardOrZero(fields.second, fields.hasSeconds) && isZero(fields.minute)) {
            if ("*".equals(fields.hour) && isWildcard(fields.dayOfMonth) && isWildcard(fields.month) && isWildcard(fields.dayOfWeek)) {
                return "Every hour";
            }
            if (fields.hour.startsWith("*/") && isWildcard(fields.dayOfMonth) && isWildcard(fields.month) && isWildcard(fields.dayOfWeek)) {
                int hourStep = parseStep(fields.hour, 1, 23);
                return "Every " + hourStep + " hours";
            }
        }

        // 4. Fixed Time Execution
        int hour = parseSingleInt(fields.hour, 0, 23, "hour");
        int minute = parseSingleInt(fields.minute, 0, 59, "minute");
        int second = fields.hasSeconds ? parseSingleInt(fields.second, 0, 59, "second") : 0;

        String timeStr = formatEnglishTime(hour, minute, second);

        // 4a. Day of Week specified
        if (!isWildcard(fields.dayOfWeek)) {
            String dowStr = formatEnglishDayOfWeek(fields.dayOfWeek);
            return "Every " + dowStr + " at " + timeStr;
        }

        // 4b. Month and Day of Month specified
        if (!isWildcard(fields.month) && !isWildcard(fields.dayOfMonth)) {
            int m = parseMonth(fields.month);
            int dom = parseSingleInt(fields.dayOfMonth, 1, 31, "day-of-month");
            return "Every " + EN_MONTHS[m] + " " + dom + " at " + timeStr;
        }

        // 4c. Day of Month specified
        if (!isWildcard(fields.dayOfMonth)) {
            int dom = parseSingleInt(fields.dayOfMonth, 1, 31, "day-of-month");
            return "Every month on day " + dom + " at " + timeStr;
        }

        // 4d. Default: Every day at time
        return "Every day at " + timeStr;
    }

    private static CronFields parseAndValidate(String cron) {
        if (cron == null || cron.trim().isEmpty()) {
            throw new IllegalArgumentException("Cron expression must not be null or blank");
        }

        String[] tokens = cron.trim().split("\\s+");
        if (tokens.length == 5) {
            return new CronFields("0", tokens[0], tokens[1], tokens[2], tokens[3], tokens[4], false);
        } else if (tokens.length == 6) {
            return new CronFields(tokens[0], tokens[1], tokens[2], tokens[3], tokens[4], tokens[5], true);
        } else {
            throw new IllegalArgumentException("Invalid cron expression format (expected 5 or 6 fields, got " + tokens.length + "): " + cron);
        }
    }

    private static boolean isWildcard(String s) {
        return "*".equals(s) || "?".equals(s);
    }

    private static boolean isZero(String s) {
        return "0".equals(s) || "00".equals(s);
    }

    private static boolean isWildcardOrZero(String s, boolean hasSeconds) {
        if (!hasSeconds) return true;
        return isZero(s) || isWildcard(s);
    }

    private static int parseStep(String token, int min, int max) {
        try {
            int step = Integer.parseInt(token.substring(2));
            if (step < min || step > max) {
                throw new IllegalArgumentException("Step out of range [" + min + ", " + max + "]: " + step);
            }
            return step;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid step format: " + token, e);
        }
    }

    private static int parseSingleInt(String token, int min, int max, String name) {
        try {
            int val = Integer.parseInt(token);
            if (val < min || val > max) {
                throw new IllegalArgumentException(name + " out of bounds [" + min + ", " + max + "]: " + val);
            }
            return val;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid number for " + name + ": " + token, e);
        }
    }

    private static int parseMonth(String token) {
        String upper = token.toUpperCase(Locale.ROOT);
        return switch (upper) {
            case "JAN", "1" -> 1;
            case "FEB", "2" -> 2;
            case "MAR", "3" -> 3;
            case "APR", "4" -> 4;
            case "MAY", "5" -> 5;
            case "JUN", "6" -> 6;
            case "JUL", "7" -> 7;
            case "AUG", "8" -> 8;
            case "SEP", "9" -> 9;
            case "OCT", "10" -> 10;
            case "NOV", "11" -> 11;
            case "DEC", "12" -> 12;
            default -> throw new IllegalArgumentException("Invalid month token: " + token);
        };
    }

    private static int parseDayOfWeekNumber(String token) {
        String upper = token.toUpperCase(Locale.ROOT);
        return switch (upper) {
            case "SUN", "0", "7" -> 0;
            case "MON", "1" -> 1;
            case "TUE", "2" -> 2;
            case "WED", "3" -> 3;
            case "THU", "4" -> 4;
            case "FRI", "5" -> 5;
            case "SAT", "6" -> 6;
            default -> throw new IllegalArgumentException("Invalid day-of-week token: " + token);
        };
    }

    private static String formatKoreanDayOfWeek(String dow) {
        if (dow.contains("-")) {
            String[] parts = dow.split("-");
            int from = parseDayOfWeekNumber(parts[0]);
            int to = parseDayOfWeekNumber(parts[1]);
            return KO_DAYS[from] + "부터 " + KO_DAYS[to] + "까지";
        }
        if (dow.contains(",")) {
            String[] parts = dow.split(",");
            List<String> list = new ArrayList<>();
            for (String p : parts) {
                list.add(KO_DAYS[parseDayOfWeekNumber(p)]);
            }
            return String.join(", ", list) + "마다";
        }
        int day = parseDayOfWeekNumber(dow);
        return KO_DAYS[day];
    }

    private static String formatEnglishDayOfWeek(String dow) {
        if (dow.contains("-")) {
            String[] parts = dow.split("-");
            int from = parseDayOfWeekNumber(parts[0]);
            int to = parseDayOfWeekNumber(parts[1]);
            return EN_DAYS[from] + " through " + EN_DAYS[to];
        }
        if (dow.contains(",")) {
            String[] parts = dow.split(",");
            List<String> list = new ArrayList<>();
            for (String p : parts) {
                list.add(EN_DAYS[parseDayOfWeekNumber(p)]);
            }
            if (list.size() == 2) {
                return list.get(0) + " and " + list.get(1);
            }
            return String.join(", ", list);
        }
        int day = parseDayOfWeekNumber(dow);
        return EN_DAYS[day];
    }

    private static String formatKoreanTime(int hour, int minute, int second) {
        int h12 = (hour == 0 || hour == 12) ? 12 : hour % 12;
        String ampm = (hour < 12) ? "오전" : "오후";
        if (second == 0) {
            return String.format("%s %d시 %02d분마다", ampm, h12, minute);
        } else {
            return String.format("%s %d시 %02d분 %02d초마다", ampm, h12, minute, second);
        }
    }

    private static String formatEnglishTime(int hour, int minute, int second) {
        if (second == 0) {
            return String.format("%02d:%02d", hour, minute);
        } else {
            return String.format("%02d:%02d:%02d", hour, minute, second);
        }
    }
}
