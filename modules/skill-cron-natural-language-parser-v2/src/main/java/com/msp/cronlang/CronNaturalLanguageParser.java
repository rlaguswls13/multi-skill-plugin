package com.msp.cronlang;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Natural language (Korean and English) to Cron expression parser V2.
 * Supports standard 5-field (Linux/Unix) and Quartz 6-field formats.
 */
public final class CronNaturalLanguageParser {

    private CronNaturalLanguageParser() {}

    public static ParsedCronResult parse(String text) {
        return tryParse(text).orElseThrow(() ->
                new IllegalArgumentException("Unsupported or unparseable cron natural language expression: '" + text + "'"));
    }

    public static Optional<ParsedCronResult> tryParse(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }

        String raw = text.trim();
        String lower = raw.toLowerCase(Locale.ROOT);

        // 1. Minute intervals: "every 15 minutes between 9am and 5pm on weekdays"
        Optional<ParsedCronResult> res = parseMinutesBetweenWeekdays(raw, lower);
        if (res.isPresent()) return res;

        // 2. Simple minute intervals: "every 10 minutes", "10분마다", "평일 15분마다"
        res = parseMinuteInterval(raw, lower);
        if (res.isPresent()) return res;

        // 3. Hourly at specific minute: "every hour at minute 15", "매시 30분"
        res = parseHourlyAtMinute(raw, lower);
        if (res.isPresent()) return res;

        // 4. Monthly last weekday: "every last Friday of the month at 6pm", "매월 마지막 금요일 오후 6시"
        res = parseMonthlyLastWeekday(raw, lower);
        if (res.isPresent()) return res;

        // 5. Monthly nth weekday: "every 2nd Tuesday at 3:30pm", "매월 둘째주 화요일 오후 3시 30분"
        res = parseMonthlyNthWeekday(raw, lower);
        if (res.isPresent()) return res;

        // 6. Monthly on specific day of month: "every month on the 15th at 3pm", "매월 15일 오후 3시"
        res = parseMonthlyOnDay(raw, lower);
        if (res.isPresent()) return res;

        // 7. Days of week: "매주 월수금 오전 9시 30분", "every Monday at 8am", "every Monday, Wednesday, Friday at 9:30am"
        res = parseDaysOfWeek(raw, lower);
        if (res.isPresent()) return res;

        // 8. Weekday / Weekend daily: "every weekday at 9am", "평일 오전 9시", "주말 오전 10시"
        res = parseWeekdayWeekendDaily(raw, lower);
        if (res.isPresent()) return res;

        // 9. Daily at specific time: "every day at midnight", "매일 밤 11시", "매일 14:00"
        res = parseDaily(raw, lower);
        if (res.isPresent()) return res;

        return Optional.empty();
    }

    // --- Parsers ---

    // "every 15 minutes between 9am and 5pm on weekdays"
    private static Optional<ParsedCronResult> parseMinutesBetweenWeekdays(String raw, String lower) {
        Pattern p = Pattern.compile("every\\s+(\\d+)\\s*(?:minutes?|mins?)\\s+between\\s+([0-9ap:m]+)\\s+and\\s+([0-9ap:m]+)\\s+on\\s+weekdays");
        Matcher m = p.matcher(lower);
        if (m.find()) {
            int step = Integer.parseInt(m.group(1));
            int startHour = parseHourOnly(m.group(2));
            int endHour = parseHourOnly(m.group(3));
            String hourRange = startHour + "-" + endHour;

            String std = "*/" + step + " " + hourRange + " * * 1-5";
            String qtz = "0 */" + step + " " + hourRange + " ? * MON-FRI";
            return Optional.of(new ParsedCronResult(std, qtz,
                    "Every " + step + " minutes between " + startHour + ":00 and " + endHour + ":00 on weekdays",
                    "Next execution within active hours on weekdays"));
        }
        return Optional.empty();
    }

    // "every 10 minutes", "10분마다", "평일 15분마다", "평일(월-금) 15분마다"
    private static Optional<ParsedCronResult> parseMinuteInterval(String raw, String lower) {
        // Korean with weekdays
        Pattern krWeekdayPattern = Pattern.compile("(?:평일|주중)(?:\\s*\\([^)일]*일?\\))?\\s*(\\d+)\\s*분마다");
        Matcher krWm = krWeekdayPattern.matcher(raw);
        if (krWm.find()) {
            int step = Integer.parseInt(krWm.group(1));
            String std = "*/" + step + " * * * 1-5";
            String qtz = "0 */" + step + " * ? * MON-FRI";
            return Optional.of(new ParsedCronResult(std, qtz, "평일 " + step + "분마다", "평일 매 " + step + "분마다 실행"));
        }

        // Korean plain
        Pattern krPattern = Pattern.compile("(?:매\\s*)?(\\d+)\\s*분마다");
        Matcher km = krPattern.matcher(raw);
        if (km.find()) {
            int step = Integer.parseInt(km.group(1));
            String std = "*/" + step + " * * * *";
            String qtz = "0 */" + step + " * * * ?";
            return Optional.of(new ParsedCronResult(std, qtz, step + "분마다", "매 " + step + "분마다 실행"));
        }

        // English plain
        Pattern enPattern = Pattern.compile("every\\s+(\\d+)\\s*(?:minutes?|mins?)");
        Matcher em = enPattern.matcher(lower);
        if (em.find()) {
            int step = Integer.parseInt(em.group(1));
            String std = "*/" + step + " * * * *";
            String qtz = "0 */" + step + " * * * ?";
            return Optional.of(new ParsedCronResult(std, qtz, "Every " + step + " minutes", "Runs every " + step + " minutes"));
        }

        return Optional.empty();
    }

    // "every hour at minute 15", "매시 30분", "매시간 15분"
    private static Optional<ParsedCronResult> parseHourlyAtMinute(String raw, String lower) {
        Pattern kr = Pattern.compile("(?:매시간|매시)\\s*(\\d+)\\s*분");
        Matcher km = kr.matcher(raw);
        if (km.find()) {
            int min = Integer.parseInt(km.group(1));
            String std = min + " * * * *";
            String qtz = "0 " + min + " * * * ?";
            return Optional.of(new ParsedCronResult(std, qtz, "매시 " + min + "분", "매 시간 " + min + "분에 실행"));
        }

        Pattern en = Pattern.compile("every\\s+hour\\s+at\\s+(?:minute\\s+)?(\\d+)");
        Matcher em = en.matcher(lower);
        if (em.find()) {
            int min = Integer.parseInt(em.group(1));
            String std = min + " * * * *";
            String qtz = "0 " + min + " * * * ?";
            return Optional.of(new ParsedCronResult(std, qtz, "Every hour at minute " + min, "Runs at " + min + " minutes past every hour"));
        }

        if (lower.equals("every hour") || raw.equals("매시간") || raw.equals("매시")) {
            return Optional.of(new ParsedCronResult("0 * * * *", "0 0 * * * ?", "매시간 정각", "Runs every hour at minute 0"));
        }

        return Optional.empty();
    }

    // "every last Friday of the month at 6pm", "매월 마지막 금요일 오후 6시"
    private static Optional<ParsedCronResult> parseMonthlyLastWeekday(String raw, String lower) {
        // Korean: 매월|매달 마지막 (요일) (시간)
        Pattern kr = Pattern.compile("(?:매월|매달)\\s*마지막\\s*([월화수목금토일])요일?\\s+(.+)");
        Matcher km = kr.matcher(raw);
        if (km.find()) {
            String dayChar = km.group(1);
            int[] time = parseTime(km.group(2));
            int dowNum = parseKoreanDayOfWeek(dayChar); // 1=MON..7=SUN
            int qtzDow = toQuartzDow(dowNum); // 1=SUN..7=SAT

            String std = time[1] + " " + time[0] + " * * " + dowNum + "L";
            String qtz = "0 " + time[1] + " " + time[0] + " ? * " + qtzDow + "L";
            return Optional.of(new ParsedCronResult(std, qtz,
                    "매월 마지막 " + dayChar + "요일 " + String.format("%02d:%02d", time[0], time[1]),
                    "매월 마지막 " + dayChar + "요일에 실행"));
        }

        // English: "every last Friday of the month at 6pm"
        Pattern en = Pattern.compile("every\\s+last\\s+([a-zA-Z]+)(?:\\s+of\\s+the\\s+month)?\\s+at\\s+(.+)");
        Matcher em = en.matcher(lower);
        if (em.find()) {
            String dowStr = em.group(1);
            int dowNum = parseEnglishDayOfWeek(dowStr);
            if (dowNum > 0) {
                int[] time = parseTime(em.group(2));
                int qtzDow = toQuartzDow(dowNum);
                String std = time[1] + " " + time[0] + " * * " + dowNum + "L";
                String qtz = "0 " + time[1] + " " + time[0] + " ? * " + qtzDow + "L";
                return Optional.of(new ParsedCronResult(std, qtz,
                        "Every last " + capitalize(dowStr) + " of the month at " + String.format("%02d:%02d", time[0], time[1]),
                        "Runs on the last " + capitalize(dowStr) + " of every month"));
            }
        }

        return Optional.empty();
    }

    // "every 2nd Tuesday at 3:30pm", "매월 둘째주 화요일 오후 3시 30분"
    private static Optional<ParsedCronResult> parseMonthlyNthWeekday(String raw, String lower) {
        // English: every (1st|2nd|3rd|4th|5th) (dow) at (time)
        Pattern en = Pattern.compile("every\\s+(\\d+)(?:st|nd|rd|th)?\\s+([a-zA-Z]+)(?:\\s+of\\s+the\\s+month)?\\s+at\\s+(.+)");
        Matcher em = en.matcher(lower);
        if (em.find()) {
            int nth = Integer.parseInt(em.group(1));
            int dowNum = parseEnglishDayOfWeek(em.group(2));
            if (dowNum > 0 && nth >= 1 && nth <= 5) {
                int[] time = parseTime(em.group(3));
                int qtzDow = toQuartzDow(dowNum);
                String std = time[1] + " " + time[0] + " * * " + dowNum + "#" + nth;
                String qtz = "0 " + time[1] + " " + time[0] + " ? * " + qtzDow + "#" + nth;
                return Optional.of(new ParsedCronResult(std, qtz,
                        "Every " + nth + "th " + capitalize(em.group(2)) + " at " + String.format("%02d:%02d", time[0], time[1]),
                        "Runs on the " + nth + "th " + capitalize(em.group(2)) + " of the month"));
            }
        }

        // Korean: 매월 (첫째주|둘째주|셋째주|넷째주|1번째|2번째) (요일) (시간)
        Pattern kr = Pattern.compile("(?:매월|매달)\\s*(첫째|둘째|셋째|넷째|다섯째|[1-5]번째)\\s*(?:주)?\\s*([월화수목금토일])요일?\\s+(.+)");
        Matcher km = kr.matcher(raw);
        if (km.find()) {
            int nth = parseKoreanNth(km.group(1));
            int dowNum = parseKoreanDayOfWeek(km.group(2));
            int[] time = parseTime(km.group(3));
            int qtzDow = toQuartzDow(dowNum);
            String std = time[1] + " " + time[0] + " * * " + dowNum + "#" + nth;
            String qtz = "0 " + time[1] + " " + time[0] + " ? * " + qtzDow + "#" + nth;
            return Optional.of(new ParsedCronResult(std, qtz,
                    "매월 " + nth + "번째 " + km.group(2) + "요일 " + String.format("%02d:%02d", time[0], time[1]),
                    "매월 " + nth + "번째 " + km.group(2) + "요일에 실행"));
        }

        return Optional.empty();
    }

    // "every month on the 15th at 3pm", "at 14:00 on the 1st of every month", "매월 15일 오후 3시"
    private static Optional<ParsedCronResult> parseMonthlyOnDay(String raw, String lower) {
        // Korean: 매월|매달 (\d+)일 (시간)
        Pattern kr = Pattern.compile("(?:매월|매달)\\s*(\\d+)\\s*일\\s+(.+)");
        Matcher km = kr.matcher(raw);
        if (km.find()) {
            int day = Integer.parseInt(km.group(1));
            int[] time = parseTime(km.group(2));
            String std = time[1] + " " + time[0] + " " + day + " * *";
            String qtz = "0 " + time[1] + " " + time[0] + " " + day + " * ?";
            return Optional.of(new ParsedCronResult(std, qtz,
                    "매월 " + day + "일 " + String.format("%02d:%02d", time[0], time[1]),
                    "매월 " + day + "일에 실행"));
        }

        // English: "every month on the 15th at 3pm"
        Pattern en1 = Pattern.compile("every\\s+month\\s+on\\s+the\\s+(\\d+)(?:st|nd|rd|th)?\\s+at\\s+(.+)");
        Matcher em1 = en1.matcher(lower);
        if (em1.find()) {
            int day = Integer.parseInt(em1.group(1));
            int[] time = parseTime(em1.group(2));
            String std = time[1] + " " + time[0] + " " + day + " * *";
            String qtz = "0 " + time[1] + " " + time[0] + " " + day + " * ?";
            return Optional.of(new ParsedCronResult(std, qtz,
                    "Every month on day " + day + " at " + String.format("%02d:%02d", time[0], time[1]),
                    "Runs on day " + day + " of every month"));
        }

        // English: "at 14:00 on the 1st of every month"
        Pattern en2 = Pattern.compile("at\\s+([0-9ap:m]+)\\s+on\\s+the\\s+(\\d+)(?:st|nd|rd|th)?\\s+of\\s+every\\s+month");
        Matcher em2 = en2.matcher(lower);
        if (em2.find()) {
            int[] time = parseTime(em2.group(1));
            int day = Integer.parseInt(em2.group(2));
            String std = time[1] + " " + time[0] + " " + day + " * *";
            String qtz = "0 " + time[1] + " " + time[0] + " " + day + " * ?";
            return Optional.of(new ParsedCronResult(std, qtz,
                    "Every month on day " + day + " at " + String.format("%02d:%02d", time[0], time[1]),
                    "Runs on day " + day + " of every month"));
        }

        return Optional.empty();
    }

    // "매주 월수금 오전 9시 30분", "every Monday at 8am", "every Monday, Wednesday, Friday at 9:30am"
    private static Optional<ParsedCronResult> parseDaysOfWeek(String raw, String lower) {
        // Korean: 매주 ([월화수목금토일, ]+) (시간)
        Pattern kr = Pattern.compile("매주\\s*([월화수목금토일, ]+?요일?|[월화수목금토일]{2,})\\s+(.+)");
        Matcher km = kr.matcher(raw);
        if (km.find()) {
            String daysStr = km.group(1);
            List<Integer> days = parseKoreanDaysOfWeekList(daysStr);
            if (!days.isEmpty()) {
                int[] time = parseTime(km.group(2));
                String stdDays = String.join(",", days.stream().map(String::valueOf).toList());
                List<String> qtzDayNames = days.stream().map(CronNaturalLanguageParser::toQuartzDayName).toList();
                String qtzDays = String.join(",", qtzDayNames);

                String std = time[1] + " " + time[0] + " * * " + stdDays;
                String qtz = "0 " + time[1] + " " + time[0] + " ? * " + qtzDays;
                return Optional.of(new ParsedCronResult(std, qtz,
                        "매주 " + daysStr + " " + String.format("%02d:%02d", time[0], time[1]),
                        "지정된 요일에 정기 실행"));
            }
        }

        // English: "every Monday, Wednesday, Friday at 9:30am", "every Monday at 8am"
        Pattern en = Pattern.compile("every\\s+([a-zA-Z,\\s]+)\\s+at\\s+(.+)");
        Matcher em = en.matcher(lower);
        if (em.find()) {
            String daysStr = em.group(1).trim();
            if (!daysStr.equals("day") && !daysStr.equals("weekday") && !daysStr.equals("month")) {
                List<Integer> days = parseEnglishDaysOfWeekList(daysStr);
                if (!days.isEmpty()) {
                    int[] time = parseTime(em.group(2));
                    String stdDays = String.join(",", days.stream().map(String::valueOf).toList());
                    List<String> qtzDayNames = days.stream().map(CronNaturalLanguageParser::toQuartzDayName).toList();
                    String qtzDays = String.join(",", qtzDayNames);

                    String std = time[1] + " " + time[0] + " * * " + stdDays;
                    String qtz = "0 " + time[1] + " " + time[0] + " ? * " + qtzDays;
                    return Optional.of(new ParsedCronResult(std, qtz,
                            "Every " + daysStr + " at " + String.format("%02d:%02d", time[0], time[1]),
                            "Runs weekly on " + daysStr));
                }
            }
        }

        return Optional.empty();
    }

    // "every weekday at 9am", "평일 오전 9시", "주말 오전 10시"
    private static Optional<ParsedCronResult> parseWeekdayWeekendDaily(String raw, String lower) {
        // Weekdays Korean: 평일(월-금)? 오전/오후 ...
        if (raw.contains("평일") || raw.contains("주중")) {
            int[] time = parseTime(raw);
            String std = time[1] + " " + time[0] + " * * 1-5";
            String qtz = "0 " + time[1] + " " + time[0] + " ? * MON-FRI";
            return Optional.of(new ParsedCronResult(std, qtz,
                    "평일 " + String.format("%02d:%02d", time[0], time[1]), "평일(월~금) 매일 실행"));
        }

        // Weekends Korean: 주말(토,일)? ...
        if (raw.contains("주말")) {
            int[] time = parseTime(raw);
            String std = time[1] + " " + time[0] + " * * 6,0";
            String qtz = "0 " + time[1] + " " + time[0] + " ? * SAT,SUN";
            return Optional.of(new ParsedCronResult(std, qtz,
                    "주말 " + String.format("%02d:%02d", time[0], time[1]), "주말(토,일) 매일 실행"));
        }

        // Weekdays English
        if (lower.contains("weekday")) {
            int[] time = parseTime(lower);
            String std = time[1] + " " + time[0] + " * * 1-5";
            String qtz = "0 " + time[1] + " " + time[0] + " ? * MON-FRI";
            return Optional.of(new ParsedCronResult(std, qtz,
                    "Every weekday at " + String.format("%02d:%02d", time[0], time[1]), "Runs Monday through Friday"));
        }

        // Weekends English
        if (lower.contains("weekend")) {
            int[] time = parseTime(lower);
            String std = time[1] + " " + time[0] + " * * 6,0";
            String qtz = "0 " + time[1] + " " + time[0] + " ? * SAT,SUN";
            return Optional.of(new ParsedCronResult(std, qtz,
                    "Every weekend at " + String.format("%02d:%02d", time[0], time[1]), "Runs Saturday and Sunday"));
        }

        return Optional.empty();
    }

    // "every day at midnight", "매일 밤 11시", "every day at 23:00"
    private static Optional<ParsedCronResult> parseDaily(String raw, String lower) {
        if (lower.contains("every day") || lower.contains("daily") || raw.contains("매일") || raw.contains("하루 한번")) {
            int[] time = parseTime(raw);
            String std = time[1] + " " + time[0] + " * * *";
            String qtz = "0 " + time[1] + " " + time[0] + " * * ?";
            return Optional.of(new ParsedCronResult(std, qtz,
                    "매일 " + String.format("%02d:%02d", time[0], time[1]), "매일 정기 실행"));
        }
        return Optional.empty();
    }

    // --- Helper Parsers ---

    public static int[] parseTime(String text) {
        if (text == null || text.isBlank()) {
            return new int[]{0, 0};
        }
        String s = text.toLowerCase(Locale.ROOT).trim();

        if (s.contains("자정") || s.contains("midnight")) {
            return new int[]{0, 0};
        }
        if (s.contains("정오") || s.contains("noon")) {
            return new int[]{12, 0};
        }

        boolean isPm = s.contains("오후") || s.contains("밤") || s.contains("저녁") || s.contains("pm");
        boolean isAm = s.contains("오전") || s.contains("아침") || s.contains("새벽") || s.contains("am");

        // Format: HH:mm
        Pattern colonPattern = Pattern.compile("(\\d{1,2})\\s*:\\s*(\\d{1,2})");
        Matcher cm = colonPattern.matcher(s);
        if (cm.find()) {
            int hour = Integer.parseInt(cm.group(1));
            int min = Integer.parseInt(cm.group(2));
            if (isPm && hour < 12) hour += 12;
            if (isAm && hour == 12) hour = 0;
            return new int[]{hour, min};
        }

        // Format: (\d+)시 (\d+)분 or (\d+)시
        Pattern krHourMin = Pattern.compile("(\\d{1,2})\\s*시(?:\\s*(\\d{1,2})\\s*분)?");
        Matcher km = krHourMin.matcher(s);
        if (km.find()) {
            int hour = Integer.parseInt(km.group(1));
            int min = km.group(2) != null ? Integer.parseInt(km.group(2)) : 0;
            if (isPm && hour < 12) hour += 12;
            if (isAm && hour == 12) hour = 0;
            return new int[]{hour, min};
        }

        // Format: (\d+)(?:am|pm)
        Pattern enAmpm = Pattern.compile("(\\d{1,2})(?:\\s*([ap]m))?");
        Matcher em = enAmpm.matcher(s);
        if (em.find()) {
            int hour = Integer.parseInt(em.group(1));
            if (isPm && hour < 12) hour += 12;
            if (isAm && hour == 12) hour = 0;
            return new int[]{hour, 0};
        }

        return new int[]{0, 0};
    }

    private static int parseHourOnly(String str) {
        return parseTime(str)[0];
    }

    private static int parseKoreanDayOfWeek(String s) {
        if (s == null) return -1;
        return switch (s.trim().charAt(0)) {
            case '월' -> 1;
            case '화' -> 2;
            case '수' -> 3;
            case '목' -> 4;
            case '금' -> 5;
            case '토' -> 6;
            case '일' -> 0;
            default -> -1;
        };
    }

    private static List<Integer> parseKoreanDaysOfWeekList(String s) {
        List<Integer> list = new ArrayList<>();
        for (char c : s.toCharArray()) {
            int d = parseKoreanDayOfWeek(String.valueOf(c));
            if (d >= 0 && !list.contains(d)) {
                list.add(d);
            }
        }
        return list;
    }

    private static int parseEnglishDayOfWeek(String s) {
        if (s == null) return -1;
        String lower = s.toLowerCase(Locale.ROOT).trim();
        if (lower.startsWith("mon")) return 1;
        if (lower.startsWith("tue")) return 2;
        if (lower.startsWith("wed")) return 3;
        if (lower.startsWith("thu")) return 4;
        if (lower.startsWith("fri")) return 5;
        if (lower.startsWith("sat")) return 6;
        if (lower.startsWith("sun")) return 0;
        return -1;
    }

    private static List<Integer> parseEnglishDaysOfWeekList(String s) {
        List<Integer> list = new ArrayList<>();
        String[] parts = s.split("(?:\\s*,\\s*|\\s+and\\s+|\\s+)");
        for (String p : parts) {
            int d = parseEnglishDayOfWeek(p.trim());
            if (d >= 0 && !list.contains(d)) {
                list.add(d);
            }
        }
        return list;
    }

    private static int toQuartzDow(int standardDow) {
        // Standard: 0=SUN, 1=MON, 2=TUE, 3=WED, 4=THU, 5=FRI, 6=SAT
        // Quartz:   1=SUN, 2=MON, 3=TUE, 4=WED, 5=THU, 6=FRI, 7=SAT
        return standardDow == 0 ? 1 : standardDow + 1;
    }

    private static String toQuartzDayName(int standardDow) {
        return switch (standardDow) {
            case 1 -> "MON";
            case 2 -> "TUE";
            case 3 -> "WED";
            case 4 -> "THU";
            case 5 -> "FRI";
            case 6 -> "SAT";
            case 0 -> "SUN";
            default -> "*";
        };
    }

    private static int parseKoreanNth(String s) {
        if (s.contains("첫") || s.contains("1")) return 1;
        if (s.contains("둘") || s.contains("2")) return 2;
        if (s.contains("셋") || s.contains("3")) return 3;
        if (s.contains("넷") || s.contains("4")) return 4;
        if (s.contains("다섯") || s.contains("5")) return 5;
        return 1;
    }

    private static String capitalize(String str) {
        if (str == null || str.isEmpty()) return "";
        return Character.toUpperCase(str.charAt(0)) + str.substring(1).toLowerCase(Locale.ROOT);
    }
}
