package com.msp.crontext;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Translates 5-field and 6-field (or 7-field) cron expressions into natural language
 * descriptions in Korean and English.
 * <p>
 * Supported features:
 * <ul>
 *   <li>Standard 5-field (minute, hour, day-of-month, month, day-of-week)</li>
 *   <li>Standard 6-field (second, minute, hour, day-of-month, month, day-of-week)</li>
 *   <li>Wildcard (<code>*</code>), step (<code>*&#47;step</code> or <code>start&#47;step</code>)</li>
 *   <li>Ranges (<code>range-range</code>), lists (<code>val,val,val</code>)</li>
 *   <li>No-specific-value (<code>?</code>)</li>
 *   <li>Last-day (<code>L</code>), last-day-of-week (e.g. <code>5L</code>)</li>
 *   <li>Nearest-weekday (e.g. <code>15W</code>), last-weekday (<code>LW</code>)</li>
 *   <li>Named months (JAN-DEC) and named days of week (SUN-SAT)</li>
 * </ul>
 */
public final class CronHumanReadable {

    private static final String[] DOW_KO = {
            "일요일", "월요일", "화요일", "수요일", "목요일", "금요일", "토요일", "일요일"
    };

    private static final String[] DOW_EN = {
            "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"
    };

    private static final String[] MONTHS_KO = {
            "", "1월", "2월", "3월", "4월", "5월", "6월", "7월", "8월", "9월", "10월", "11월", "12월"
    };

    private static final String[] MONTHS_EN = {
            "", "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
    };

    private static final Pattern STEP_PATTERN = Pattern.compile("^(\\*|\\d+)/(\\d+)$");
    private static final Pattern RANGE_PATTERN = Pattern.compile("^([A-Za-z0-9]+)-([A-Za-z0-9]+)$");
    private static final Pattern LAST_DOW_PATTERN = Pattern.compile("^([0-7]|[A-Za-z]{3})L$", Pattern.CASE_INSENSITIVE);
    private static final Pattern NEAREST_WEEKDAY_PATTERN = Pattern.compile("^(\\d+)W$", Pattern.CASE_INSENSITIVE);

    private CronHumanReadable() {
    }

    /**
     * Translates a cron expression into a natural language description using Korean (default).
     *
     * @param cron cron expression
     * @return Korean natural language description
     */
    public static String describe(String cron) {
        return describe(cron, Locale.KOREAN);
    }

    /**
     * Translates a cron expression into a natural language description for the given locale.
     * Supported locales: {@link Locale#KOREAN} / {@link Locale#KOREA} and English (default fallback).
     *
     * @param cron   cron expression
     * @param locale target locale (Korean or English)
     * @return natural language description
     */
    public static String describe(String cron, Locale locale) {
        CronExpression expr = CronExpression.parse(cron);
        boolean isKorean = locale != null && "ko".equalsIgnoreCase(locale.getLanguage());

        if (isKorean) {
            return describeKorean(expr);
        } else {
            return describeEnglish(expr);
        }
    }

    // =========================================================================
    // Korean Description Logic
    // =========================================================================

    private static String describeKorean(CronExpression expr) {
        // Special case: Every second
        if (expr.hasSeconds() && expr.second().equals("*") && isAllWildcards(expr.minute(), expr.hour(), expr.dayOfMonth(), expr.month(), expr.dayOfWeek())) {
            return "매초";
        }

        // Special case: Every N seconds
        if (expr.hasSeconds() && expr.second().startsWith("*/") && isAllWildcards(expr.minute(), expr.hour(), expr.dayOfMonth(), expr.month(), expr.dayOfWeek())) {
            String step = expr.second().substring(2);
            return step + "초마다";
        }

        // Special case: Every minute
        if (expr.minute().equals("*") && isAllWildcards(expr.hour(), expr.dayOfMonth(), expr.month(), expr.dayOfWeek())
                && (!expr.hasSeconds() || expr.second().equals("0") || expr.second().equals("*"))) {
            return "매 분";
        }

        // Special case: Every N minutes
        if (expr.minute().startsWith("*/") && isAllWildcards(expr.hour(), expr.dayOfMonth(), expr.month(), expr.dayOfWeek())
                && (!expr.hasSeconds() || expr.second().equals("0"))) {
            String step = expr.minute().substring(2);
            return step + "분마다";
        }

        // Special case: Every hour on the hour
        if (expr.minute().equals("0") && expr.hour().equals("*") && isAllWildcards(expr.dayOfMonth(), expr.month(), expr.dayOfWeek())
                && (!expr.hasSeconds() || expr.second().equals("0"))) {
            return "매 시 정각";
        }

        // Special case: Every hour at minute X
        if (isInteger(expr.minute()) && expr.hour().equals("*") && isAllWildcards(expr.dayOfMonth(), expr.month(), expr.dayOfWeek())
                && (!expr.hasSeconds() || expr.second().equals("0"))) {
            return "매 시 " + expr.minute() + "분";
        }

        // Special case: Every N hours
        if (expr.minute().equals("0") && expr.hour().startsWith("*/") && isAllWildcards(expr.dayOfMonth(), expr.month(), expr.dayOfWeek())
                && (!expr.hasSeconds() || expr.second().equals("0"))) {
            String step = expr.hour().substring(2);
            return step + "시간마다 정각에";
        }

        // Build descriptive sentence: [Year] [Month] [Day / DayOfWeek] [Time]
        StringBuilder sb = new StringBuilder();

        // 1. Year (if specified)
        if (expr.hasYear()) {
            sb.append(expr.year()).append("년 ");
        }

        // 2. Month
        String monthDesc = describeMonthKorean(expr.month());
        if (!monthDesc.isEmpty()) {
            sb.append(monthDesc).append(" ");
        }

        // 3. Day of month & Day of week
        String dayDesc = describeDayKorean(expr.dayOfMonth(), expr.dayOfWeek(), !monthDesc.isEmpty());
        if (!dayDesc.isEmpty()) {
            sb.append(dayDesc).append(" ");
        }

        // 4. Time
        String timeDesc = describeTimeKorean(expr);
        sb.append(timeDesc);

        return sb.toString().trim().replaceAll("\\s+", " ");
    }

    private static String describeMonthKorean(String month) {
        if (isAny(month)) {
            return "";
        }
        if (month.startsWith("*/")) {
            return month.substring(2) + "개월마다";
        }
        Matcher rm = RANGE_PATTERN.matcher(month);
        if (rm.matches()) {
            return normalizeMonthKo(rm.group(1)) + "부터 " + normalizeMonthKo(rm.group(2)) + "까지";
        }
        if (month.contains(",")) {
            String[] tokens = month.split(",");
            List<String> list = new ArrayList<>();
            for (String t : tokens) {
                list.add(normalizeMonthKo(t));
            }
            return String.join(", ", list);
        }
        return "매년 " + normalizeMonthKo(month);
    }

    private static String describeDayKorean(String dom, String dow, boolean hasMonth) {
        boolean domAny = isAny(dom);
        boolean dowAny = isAny(dow);

        if (domAny && dowAny) {
            return hasMonth ? "" : "매일";
        }

        if (!dowAny && domAny) {
            // Day of week only
            return describeDowKorean(dow);
        }

        if (!domAny && dowAny) {
            // Day of month only
            return describeDomKorean(dom, hasMonth);
        }

        // Both specified
        return describeDomKorean(dom, hasMonth) + " 및 " + describeDowKorean(dow);
    }

    private static String describeDomKorean(String dom, boolean hasMonth) {
        String prefix = hasMonth ? "" : "매월 ";
        if ("L".equalsIgnoreCase(dom)) {
            return prefix + "말일";
        }
        if ("LW".equalsIgnoreCase(dom)) {
            return prefix + "마지막 평일";
        }
        Matcher wm = NEAREST_WEEKDAY_PATTERN.matcher(dom);
        if (wm.matches()) {
            return prefix + wm.group(1) + "일에서 가장 가까운 평일";
        }
        if (dom.contains(",")) {
            String[] tokens = dom.split(",");
            List<String> list = new ArrayList<>();
            for (String t : tokens) {
                list.add(t + "일");
            }
            return prefix + String.join(", ", list);
        }
        Matcher rm = RANGE_PATTERN.matcher(dom);
        if (rm.matches()) {
            return prefix + rm.group(1) + "일부터 " + rm.group(2) + "일까지";
        }
        return prefix + dom + "일";
    }

    private static String describeDowKorean(String dow) {
        Matcher lm = LAST_DOW_PATTERN.matcher(dow);
        if (lm.matches()) {
            String dayName = normalizeDowKo(lm.group(1));
            return "매월 마지막 " + dayName;
        }
        Matcher rm = RANGE_PATTERN.matcher(dow);
        if (rm.matches()) {
            String d1 = normalizeDowKo(rm.group(1));
            String d2 = normalizeDowKo(rm.group(2));
            return d1 + "부터 " + d2 + "까지";
        }
        if (dow.contains(",")) {
            String[] tokens = dow.split(",");
            List<String> list = new ArrayList<>();
            for (String t : tokens) {
                list.add(normalizeDowKo(t));
            }
            return String.join(", ", list);
        }
        return "매주 " + normalizeDowKo(dow);
    }

    private static String describeTimeKorean(CronExpression expr) {
        String sec = expr.second();
        String min = expr.minute();
        String hr = expr.hour();

        // Range of hours (e.g. 9-18)
        Matcher hrm = RANGE_PATTERN.matcher(hr);
        if (hrm.matches() && min.equals("0")) {
            int h1 = Integer.parseInt(hrm.group(1));
            int h2 = Integer.parseInt(hrm.group(2));
            return String.format("%02d:00부터 %02d:00까지 매 시 정각", h1, h2);
        }

        // Specific hour and minute
        if (isInteger(hr) && isInteger(min)) {
            int h = Integer.parseInt(hr);
            int m = Integer.parseInt(min);
            if (expr.hasSeconds() && isInteger(sec)) {
                int s = Integer.parseInt(sec);
                return String.format("%02d:%02d:%02d", h, m, s);
            }
            return String.format("%02d:%02d", h, m);
        }

        if (hr.equals("*") && isInteger(min)) {
            return "매 시 " + min + "분";
        }

        if (hr.startsWith("*/") && min.equals("0")) {
            return hr.substring(2) + "시간마다 정각에";
        }

        if (min.startsWith("*/")) {
            return min.substring(2) + "분마다";
        }

        return hr + "시 " + min + "분";
    }

    // =========================================================================
    // English Description Logic
    // =========================================================================

    private static String describeEnglish(CronExpression expr) {
        // Special case: Every second
        if (expr.hasSeconds() && expr.second().equals("*") && isAllWildcards(expr.minute(), expr.hour(), expr.dayOfMonth(), expr.month(), expr.dayOfWeek())) {
            return "Every second";
        }

        // Special case: Every N seconds
        if (expr.hasSeconds() && expr.second().startsWith("*/") && isAllWildcards(expr.minute(), expr.hour(), expr.dayOfMonth(), expr.month(), expr.dayOfWeek())) {
            String step = expr.second().substring(2);
            return "Every " + step + " seconds";
        }

        // Special case: Every minute
        if (expr.minute().equals("*") && isAllWildcards(expr.hour(), expr.dayOfMonth(), expr.month(), expr.dayOfWeek())
                && (!expr.hasSeconds() || expr.second().equals("0") || expr.second().equals("*"))) {
            return "Every minute";
        }

        // Special case: Every N minutes
        if (expr.minute().startsWith("*/") && isAllWildcards(expr.hour(), expr.dayOfMonth(), expr.month(), expr.dayOfWeek())
                && (!expr.hasSeconds() || expr.second().equals("0"))) {
            String step = expr.minute().substring(2);
            return "Every " + step + " minutes";
        }

        // Special case: Every hour on the hour
        if (expr.minute().equals("0") && expr.hour().equals("*") && isAllWildcards(expr.dayOfMonth(), expr.month(), expr.dayOfWeek())
                && (!expr.hasSeconds() || expr.second().equals("0"))) {
            return "Every hour on the hour";
        }

        // Special case: Every hour at minute X
        if (isInteger(expr.minute()) && expr.hour().equals("*") && isAllWildcards(expr.dayOfMonth(), expr.month(), expr.dayOfWeek())
                && (!expr.hasSeconds() || expr.second().equals("0"))) {
            return "Every hour at minute " + expr.minute();
        }

        // Special case: Every N hours
        if (expr.minute().equals("0") && expr.hour().startsWith("*/") && isAllWildcards(expr.dayOfMonth(), expr.month(), expr.dayOfWeek())
                && (!expr.hasSeconds() || expr.second().equals("0"))) {
            String step = expr.hour().substring(2);
            return "Every " + step + " hours on the hour";
        }

        // Standard construction: [Frequency / Day] at [Time]
        StringBuilder sb = new StringBuilder();

        // 1. DOW range with hour range
        Matcher hrm = RANGE_PATTERN.matcher(expr.hour());
        Matcher dowRm = RANGE_PATTERN.matcher(expr.dayOfWeek());
        if (hrm.matches() && expr.minute().equals("0") && isAny(expr.dayOfMonth()) && isAny(expr.month())) {
            int h1 = Integer.parseInt(hrm.group(1));
            int h2 = Integer.parseInt(hrm.group(2));
            String timeRange = String.format("Every hour from %02d:00 to %02d:00", h1, h2);
            if (dowRm.matches()) {
                String d1 = normalizeDowEn(dowRm.group(1));
                String d2 = normalizeDowEn(dowRm.group(2));
                return timeRange + ", " + d1 + " through " + d2;
            } else if (isAny(expr.dayOfWeek())) {
                return timeRange + " every day";
            }
        }

        // 2. Day & Month context
        String dayDesc = describeDayEnglish(expr.dayOfMonth(), expr.dayOfWeek(), expr.month());
        String timeDesc = describeTimeEnglish(expr);

        if (!dayDesc.isEmpty()) {
            sb.append(dayDesc).append(" at ").append(timeDesc);
        } else {
            sb.append("At ").append(timeDesc);
        }

        if (expr.hasYear()) {
            sb.append(", in year ").append(expr.year());
        }

        return sb.toString().trim().replaceAll("\\s+", " ");
    }

    private static String describeDayEnglish(String dom, String dow, String month) {
        boolean domAny = isAny(dom);
        boolean dowAny = isAny(dow);
        boolean monthAny = isAny(month);

        String monthName = monthAny ? "" : " of " + normalizeMonthEn(month);

        if (domAny && dowAny) {
            return monthAny ? "Every day" : "Every day in " + normalizeMonthEn(month);
        }

        if (!dowAny && domAny) {
            // Day of week
            Matcher lm = LAST_DOW_PATTERN.matcher(dow);
            if (lm.matches()) {
                return "On the last " + normalizeDowEn(lm.group(1)) + " of the month";
            }
            Matcher rm = RANGE_PATTERN.matcher(dow);
            if (rm.matches()) {
                return "Every " + normalizeDowEn(rm.group(1)) + " through " + normalizeDowEn(rm.group(2));
            }
            if (dow.contains(",")) {
                String[] tokens = dow.split(",");
                List<String> list = new ArrayList<>();
                for (String t : tokens) {
                    list.add(normalizeDowEn(t));
                }
                return "Every " + String.join(", ", list);
            }
            return "Every " + normalizeDowEn(dow);
        }

        if (!domAny && dowAny) {
            // Day of month
            if ("L".equalsIgnoreCase(dom)) {
                return "On the last day" + (monthAny ? " of the month" : monthName);
            }
            if ("LW".equalsIgnoreCase(dom)) {
                return "On the last weekday" + (monthAny ? " of the month" : monthName);
            }
            Matcher wm = NEAREST_WEEKDAY_PATTERN.matcher(dom);
            if (wm.matches()) {
                return "On the nearest weekday to day " + wm.group(1) + (monthAny ? " of the month" : monthName);
            }
            if (dom.contains(",")) {
                return "On day " + dom.replace(",", " and ") + (monthAny ? " of the month" : monthName);
            }
            Matcher rm = RANGE_PATTERN.matcher(dom);
            if (rm.matches()) {
                return "Between day " + rm.group(1) + " and " + rm.group(2) + (monthAny ? " of the month" : monthName);
            }
            return "On day " + dom + (monthAny ? " of the month" : monthName);
        }

        return "On day " + dom + monthName + " and every " + describeDowEnglish(dow);
    }

    private static String describeDowEnglish(String dow) {
        if (dow.contains(",")) {
            String[] tokens = dow.split(",");
            List<String> list = new ArrayList<>();
            for (String t : tokens) {
                list.add(normalizeDowEn(t));
            }
            return String.join(", ", list);
        }
        return normalizeDowEn(dow);
    }

    private static String describeTimeEnglish(CronExpression expr) {
        String sec = expr.second();
        String min = expr.minute();
        String hr = expr.hour();

        if (isInteger(hr) && isInteger(min)) {
            int h = Integer.parseInt(hr);
            int m = Integer.parseInt(min);
            if (expr.hasSeconds() && isInteger(sec)) {
                int s = Integer.parseInt(sec);
                return String.format("%02d:%02d:%02d", h, m, s);
            }
            return String.format("%02d:%02d", h, m);
        }

        if (hr.equals("*") && isInteger(min)) {
            return "minute " + min + " past every hour";
        }

        return hr + ":" + min;
    }

    // =========================================================================
    // Helpers & Normalizers
    // =========================================================================

    private static boolean isAny(String value) {
        return value == null || value.equals("*") || value.equals("?");
    }

    private static boolean isAllWildcards(String... values) {
        for (String v : values) {
            if (!isAny(v)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isInteger(String s) {
        if (s == null || s.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) return false;
        }
        return true;
    }

    private static String normalizeDowKo(String val) {
        int idx = parseDowIndex(val);
        if (idx >= 0 && idx < DOW_KO.length) {
            return DOW_KO[idx];
        }
        return val;
    }

    private static String normalizeDowEn(String val) {
        int idx = parseDowIndex(val);
        if (idx >= 0 && idx < DOW_EN.length) {
            return DOW_EN[idx];
        }
        return val;
    }

    private static int parseDowIndex(String val) {
        if (isInteger(val)) {
            int v = Integer.parseInt(val);
            if (v >= 0 && v <= 7) return v;
        }
        return switch (val.toUpperCase(Locale.ROOT)) {
            case "SUN" -> 0;
            case "MON" -> 1;
            case "TUE" -> 2;
            case "WED" -> 3;
            case "THU" -> 4;
            case "FRI" -> 5;
            case "SAT" -> 6;
            default -> -1;
        };
    }

    private static String normalizeMonthKo(String val) {
        int m = parseMonthIndex(val);
        if (m >= 1 && m <= 12) {
            return MONTHS_KO[m];
        }
        return val;
    }

    private static String normalizeMonthEn(String val) {
        int m = parseMonthIndex(val);
        if (m >= 1 && m <= 12) {
            return MONTHS_EN[m];
        }
        return val;
    }

    private static int parseMonthIndex(String val) {
        if (isInteger(val)) {
            int v = Integer.parseInt(val);
            if (v >= 1 && v <= 12) return v;
        }
        return switch (val.toUpperCase(Locale.ROOT)) {
            case "JAN" -> 1;
            case "FEB" -> 2;
            case "MAR" -> 3;
            case "APR" -> 4;
            case "MAY" -> 5;
            case "JUN" -> 6;
            case "JUL" -> 7;
            case "AUG" -> 8;
            case "SEP" -> 9;
            case "OCT" -> 10;
            case "NOV" -> 11;
            case "DEC" -> 12;
            default -> -1;
        };
    }
}
