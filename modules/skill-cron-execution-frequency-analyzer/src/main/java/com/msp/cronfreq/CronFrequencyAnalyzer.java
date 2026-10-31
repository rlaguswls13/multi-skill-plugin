package com.msp.cronfreq;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;

/**
 * Pure Java 21 Cron expression execution frequency analyzer and peak load detector.
 * <p>
 * Evaluates standard 5-field crontab expressions:
 * <pre>
 *   ┌───────────── minute (0 - 59)
 *   │ ┌───────────── hour (0 - 23)
 *   │ │ ┌───────────── day of the month (1 - 31)
 *   │ │ │ ┌───────────── month (1 - 12 or JAN - DEC)
 *   │ │ │ │ ┌───────────── day of the week (0 - 7 or SUN - SAT, 0 and 7 = SUN)
 *   * * * * *
 * </pre>
 */
public final class CronFrequencyAnalyzer {

    private static final int REFERENCE_YEAR = 2026; // Non-leap year starting Thursday

    private CronFrequencyAnalyzer() {
        // utility class
    }

    /**
     * Analyzes execution frequency, yearly/monthly/weekly/daily projections, and hourly/day-of-week distributions.
     *
     * @param cron cron expression
     * @return populated frequency report
     */
    public static CronFrequencyReport analyze(String cron) {
        Objects.requireNonNull(cron, "Cron expression must not be null");
        ParsedCron parsed = parse(cron.trim());

        int[] hourlyHist = new int[24];
        int[] dowHist = new int[7];
        long yearlyTotal = 0;

        // Count matching minutes per hour when hour matches
        int matchingMinuteCount = 0;
        for (int m = 0; m < 60; m++) {
            if (parsed.minutes[m]) {
                matchingMinuteCount++;
            }
        }

        LocalDate date = LocalDate.of(REFERENCE_YEAR, Month.JANUARY, 1);
        int totalDays = date.lengthOfYear();

        for (int d = 0; d < totalDays; d++) {
            LocalDate current = date.plusDays(d);
            int month = current.getMonthValue();
            int dayOfMonth = current.getDayOfMonth();
            int dow = current.getDayOfWeek().getValue() % 7; // 1=MON..6=SAT, 7=SUN -> 0=SUN

            if (!parsed.months[month]) {
                continue;
            }

            boolean dayMatches;
            if (parsed.domRestricted && parsed.dowRestricted) {
                // Standard crontab union behavior: if both DOM and DOW are specified, day matches if either matches
                dayMatches = parsed.daysOfMonth[dayOfMonth] || parsed.daysOfWeek[dow];
            } else if (parsed.domRestricted) {
                dayMatches = parsed.daysOfMonth[dayOfMonth];
            } else if (parsed.dowRestricted) {
                dayMatches = parsed.daysOfWeek[dow];
            } else {
                dayMatches = true;
            }

            if (!dayMatches) {
                continue;
            }

            int dayFires = 0;
            for (int h = 0; h < 24; h++) {
                if (parsed.hours[h]) {
                    hourlyHist[h] += matchingMinuteCount;
                    dayFires += matchingMinuteCount;
                }
            }

            dowHist[dow] += dayFires;
            yearlyTotal += dayFires;
        }

        long dailyCount = Math.round((double) yearlyTotal / totalDays);
        long weeklyCount = Math.round((double) yearlyTotal / (totalDays / 7.0));
        long monthlyCount = Math.round((double) yearlyTotal / 12.0);

        return new CronFrequencyReport(dailyCount, weeklyCount, monthlyCount, yearlyTotal, hourlyHist, dowHist);
    }

    /**
     * Determines the peak execution hour (0 to 23) with the highest frequency of triggers.
     *
     * @param cron cron expression
     * @return peak hour index (0 - 23)
     */
    public static int getPeakHour(String cron) {
        CronFrequencyReport report = analyze(cron);
        int[] hist = report.hourlyHistogram();
        int maxHour = 0;
        int maxVal = -1;
        for (int h = 0; h < 24; h++) {
            if (hist[h] > maxVal) {
                maxVal = hist[h];
                maxHour = h;
            }
        }
        return maxHour;
    }

    /**
     * Renders a human-readable ASCII horizontal bar chart representing the hourly execution distribution.
     *
     * @param cron cron expression
     * @return formatted multi-line ASCII chart
     */
    public static String toAsciiHourlyChart(String cron) {
        CronFrequencyReport report = analyze(cron);
        int[] hist = report.hourlyHistogram();
        int max = 0;
        for (int val : hist) {
            if (val > max) {
                max = val;
            }
        }

        int maxBarWidth = 24;
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.ROOT, "Hourly Trigger Distribution for [%s] (Total Yearly: %,d):%n", cron.trim(), report.yearlyCount()));

        for (int h = 0; h < 24; h++) {
            int count = hist[h];
            int barLength = (max == 0) ? 0 : (int) Math.round(((double) count / max) * maxBarWidth);
            sb.append(String.format(Locale.ROOT, "%02d:00 [", h));
            sb.append("█".repeat(barLength));
            sb.append(" ".repeat(maxBarWidth - barLength));
            sb.append(String.format(Locale.ROOT, "] %,d%n", count));
        }

        return sb.toString();
    }

    private static ParsedCron parse(String cron) {
        // Macro replacements
        String expression = switch (cron.toLowerCase(Locale.ROOT)) {
            case "@yearly", "@annually" -> "0 0 1 1 *";
            case "@monthly" -> "0 0 1 * *";
            case "@weekly" -> "0 0 * * 0";
            case "@daily", "@midnight" -> "0 0 * * *";
            case "@hourly" -> "0 * * * *";
            default -> cron;
        };

        String[] parts = expression.trim().split("\\s+");
        if (parts.length != 5) {
            throw new IllegalArgumentException("Invalid cron expression format (expected 5 fields): " + cron);
        }

        boolean[] minutes = parseField(parts[0], 0, 59, null);
        boolean[] hours = parseField(parts[1], 0, 23, null);

        boolean domRestricted = !parts[2].equals("*") && !parts[2].equals("?");
        boolean[] dom = parseField(parts[2].replace('?', '*'), 1, 31, null);

        boolean[] months = parseField(parts[3], 1, 12, MONTH_NAMES);

        boolean dowRestricted = !parts[4].equals("*") && !parts[4].equals("?");
        boolean[] dow = parseDowField(parts[4].replace('?', '*'));

        return new ParsedCron(minutes, hours, dom, months, dow, domRestricted, dowRestricted);
    }

    private static final String[] MONTH_NAMES = {
            "JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC"
    };

    private static final String[] DOW_NAMES = {
            "SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT"
    };

    private static boolean[] parseDowField(String field) {
        boolean[] raw = parseField(field, 0, 7, DOW_NAMES);
        boolean[] dow = new boolean[7];
        // 0 and 7 both represent Sunday
        dow[0] = raw[0] || raw[7];
        for (int i = 1; i <= 6; i++) {
            dow[i] = raw[i];
        }
        return dow;
    }

    private static boolean[] parseField(String field, int min, int max, String[] names) {
        boolean[] bits = new boolean[max + 1];
        if (field.equals("*")) {
            Arrays.fill(bits, min, max + 1, true);
            return bits;
        }

        String[] subParts = field.split(",");
        for (String sub : subParts) {
            sub = sub.trim();
            if (sub.isEmpty()) continue;

            int step = 1;
            String rangePart = sub;
            if (sub.contains("/")) {
                String[] stepTokens = sub.split("/");
                rangePart = stepTokens[0];
                step = Integer.parseInt(stepTokens[1]);
                if (step <= 0) {
                    throw new IllegalArgumentException("Step must be positive: " + sub);
                }
            }

            int rangeStart;
            int rangeEnd;

            if (rangePart.equals("*") || rangePart.isEmpty()) {
                rangeStart = min;
                rangeEnd = max;
            } else if (rangePart.contains("-")) {
                String[] rangeTokens = rangePart.split("-");
                rangeStart = parseNameOrNumber(rangeTokens[0], min, max, names);
                rangeEnd = parseNameOrNumber(rangeTokens[1], min, max, names);
            } else {
                rangeStart = parseNameOrNumber(rangePart, min, max, names);
                rangeEnd = (sub.contains("/")) ? max : rangeStart;
            }

            for (int i = rangeStart; i <= rangeEnd; i += step) {
                if (i >= min && i <= max) {
                    bits[i] = true;
                }
            }
        }

        return bits;
    }

    private static int parseNameOrNumber(String token, int min, int max, String[] names) {
        token = token.trim();
        if (names != null) {
            String upper = token.toUpperCase(Locale.ROOT);
            for (int i = 0; i < names.length; i++) {
                if (names[i].equals(upper)) {
                    return (min == 1) ? (i + 1) : i; // 1-based for months, 0-based for dow
                }
            }
        }

        try {
            int val = Integer.parseInt(token);
            if (val < min || val > max) {
                throw new IllegalArgumentException("Value " + val + " out of bounds [" + min + ", " + max + "]");
            }
            return val;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid token: " + token, e);
        }
    }

    private record ParsedCron(
            boolean[] minutes,
            boolean[] hours,
            boolean[] daysOfMonth,
            boolean[] months,
            boolean[] daysOfWeek,
            boolean domRestricted,
            boolean dowRestricted
    ) {
    }
}
