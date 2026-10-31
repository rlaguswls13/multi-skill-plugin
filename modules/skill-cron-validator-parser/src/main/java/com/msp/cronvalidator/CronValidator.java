package com.msp.cronvalidator;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * High-precision validator and natural-language descriptor for 5-field and 6-field Cron expressions.
 * Pure Java 21, zero external dependencies.
 */
public final class CronValidator {

    private CronValidator() {
        // Utility class
    }

    /**
     * Checks whether the given Cron expression is syntactically valid.
     */
    public static boolean isValid(String cron) {
        return validate(cron).valid();
    }

    /**
     * Validates and parses each field in a Cron expression.
     *
     * @param cron Cron expression (5 or 6 fields)
     * @return validation result containing details for each field
     */
    public static CronValidationResult validate(String cron) {
        if (cron == null || cron.isBlank()) {
            return CronValidationResult.error(cron, "Cron expression must not be null or blank");
        }

        String[] parts = cron.trim().split("\\s+");
        if (parts.length != 5 && parts.length != 6) {
            return CronValidationResult.error(
                    cron,
                    "Invalid number of fields: expected 5 or 6, but found " + parts.length
            );
        }

        boolean hasSeconds = (parts.length == 6);
        CronFieldType[] fieldTypes = hasSeconds
                ? new CronFieldType[]{
                CronFieldType.SECOND, CronFieldType.MINUTE, CronFieldType.HOUR,
                CronFieldType.DAY_OF_MONTH, CronFieldType.MONTH, CronFieldType.DAY_OF_WEEK
        }
                : new CronFieldType[]{
                CronFieldType.MINUTE, CronFieldType.HOUR,
                CronFieldType.DAY_OF_MONTH, CronFieldType.MONTH, CronFieldType.DAY_OF_WEEK
        };

        List<CronFieldDetail> fieldDetails = new ArrayList<>();
        boolean allValid = true;
        StringBuilder overallError = new StringBuilder();

        for (int i = 0; i < parts.length; i++) {
            CronFieldType type = fieldTypes[i];
            String expr = parts[i];
            FieldCheckResult check = checkField(type, expr);

            if (check.valid()) {
                fieldDetails.add(CronFieldDetail.valid(type, expr, check.description()));
            } else {
                allValid = false;
                fieldDetails.add(CronFieldDetail.invalid(type, expr, check.errorMessage()));
                if (!overallError.isEmpty()) {
                    overallError.append("; ");
                }
                overallError.append(type.getLabel()).append(": ").append(check.errorMessage());
            }
        }

        if (!allValid) {
            return CronValidationResult.error(cron, overallError.toString(), fieldDetails);
        }

        return CronValidationResult.success(cron, fieldDetails);
    }

    /**
     * Produces a human-readable description of the Cron expression.
     */
    public static String describe(String cron) {
        CronValidationResult result = validate(cron);
        if (!result.valid()) {
            return "Invalid cron expression: " + result.errorMessage();
        }

        List<CronFieldDetail> fields = result.fields();
        boolean hasSeconds = (fields.size() == 6);

        String sec = hasSeconds ? fields.get(0).expression() : null;
        String min = fields.get(hasSeconds ? 1 : 0).expression();
        String hr = fields.get(hasSeconds ? 2 : 1).expression();
        String dom = fields.get(hasSeconds ? 3 : 2).expression();
        String mon = fields.get(hasSeconds ? 4 : 3).expression();
        String dow = fields.get(hasSeconds ? 5 : 4).expression();

        // 1. Every N minutes / Every minute
        if (min.startsWith("*/") && isAllOrWild(hr) && isAllOrWild(dom) && isAllOrWild(mon) && isAllOrWild(dow)) {
            String step = min.substring(2);
            return "Every " + step + " minutes";
        }
        if (min.equals("*") && isAllOrWild(hr) && isAllOrWild(dom) && isAllOrWild(mon) && isAllOrWild(dow)) {
            return hasSeconds && !sec.equals("0") ? "Every minute at second " + sec : "Every minute";
        }

        // 2. Every hour
        if (isSingleInteger(min) && hr.equals("*") && isAllOrWild(dom) && isAllOrWild(mon) && isAllOrWild(dow)) {
            return "Every hour at minute " + min;
        }

        // 3. Specific time with day/month conditions
        if (isSingleInteger(min) && isSingleInteger(hr) && isAllOrWild(dom)) {
            String timeStr = formatTime(hr, min, sec);
            if (isAllOrWild(mon) && isAllOrWild(dow)) {
                return "At " + timeStr + " every day";
            }
            if (isAllOrWild(mon) && !isAllOrWild(dow)) {
                return "At " + timeStr + ", only on " + formatDayOfWeek(dow);
            }
            if (!isAllOrWild(mon) && isAllOrWild(dow)) {
                return "At " + timeStr + ", only in " + mon;
            }
            return "At " + timeStr + ", in " + mon + ", only on " + formatDayOfWeek(dow);
        }

        // 5. Fallback structured description
        StringBuilder sb = new StringBuilder();
        if (hasSeconds && !sec.equals("0")) {
            sb.append("At second ").append(sec).append(", ");
        }
        sb.append("minute ").append(min)
                .append(", hour ").append(hr)
                .append(", day of month ").append(dom)
                .append(", month ").append(mon)
                .append(", day of week ").append(dow);

        return sb.toString();
    }

    private static boolean isAllOrWild(String expr) {
        return expr.equals("*") || expr.equals("?");
    }

    private static boolean isSingleInteger(String expr) {
        try {
            Integer.parseInt(expr);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static String formatTime(String hour, String minute, String second) {
        int h = Integer.parseInt(hour);
        int m = Integer.parseInt(minute);
        if (second != null && isSingleInteger(second)) {
            int s = Integer.parseInt(second);
            return String.format("%02d:%02d:%02d", h, m, s);
        }
        return String.format("%02d:%02d", h, m);
    }

    private static String formatDayOfWeek(String dow) {
        String upper = dow.toUpperCase(Locale.ROOT);
        return switch (upper) {
            case "1", "MON" -> "Monday";
            case "2", "TUE" -> "Tuesday";
            case "3", "WED" -> "Wednesday";
            case "4", "THU" -> "Thursday";
            case "5", "FRI" -> "Friday";
            case "6", "SAT" -> "Saturday";
            case "0", "7", "SUN" -> "Sunday";
            case "MON-FRI", "1-5" -> "Monday through Friday";
            default -> dow;
        };
    }

    // --- Field-level parser and validator ---

    private record FieldCheckResult(boolean valid, String description, String errorMessage) {
        static FieldCheckResult ok(String description) {
            return new FieldCheckResult(true, description, null);
        }

        static FieldCheckResult fail(String errorMessage) {
            return new FieldCheckResult(false, null, errorMessage);
        }
    }

    private static FieldCheckResult checkField(CronFieldType type, String expr) {
        String trimmed = expr.trim();
        if (trimmed.isEmpty()) {
            return FieldCheckResult.fail("Field expression is empty");
        }

        // Comma-separated values (e.g. 1,5,10)
        String[] parts = trimmed.split(",");
        List<String> subDescriptions = new ArrayList<>();

        for (String sub : parts) {
            FieldCheckResult subCheck = checkSubExpression(type, sub.trim());
            if (!subCheck.valid()) {
                return subCheck;
            }
            subDescriptions.add(subCheck.description());
        }

        return FieldCheckResult.ok(String.join(", ", subDescriptions));
    }

    private static FieldCheckResult checkSubExpression(CronFieldType type, String sub) {
        if (sub.equals("*")) {
            return FieldCheckResult.ok("every " + type.getLabel());
        }

        if (sub.equals("?")) {
            if (type == CronFieldType.DAY_OF_MONTH || type == CronFieldType.DAY_OF_WEEK) {
                return FieldCheckResult.ok("no specific " + type.getLabel());
            }
            return FieldCheckResult.fail("'?' is only allowed for day-of-month and day-of-week");
        }

        // Special characters for Day-of-month and Day-of-week
        if (type == CronFieldType.DAY_OF_MONTH) {
            if (sub.equals("L")) {
                return FieldCheckResult.ok("last day of the month");
            }
            if (sub.equals("LW")) {
                return FieldCheckResult.ok("last weekday of the month");
            }
            if (sub.endsWith("W")) {
                String dayNum = sub.substring(0, sub.length() - 1);
                try {
                    int day = Integer.parseInt(dayNum);
                    if (day < 1 || day > 31) {
                        return FieldCheckResult.fail("Day number in 'W' must be 1-31: " + day);
                    }
                    return FieldCheckResult.ok("nearest weekday to day " + day);
                } catch (NumberFormatException e) {
                    return FieldCheckResult.fail("Invalid 'W' expression: " + sub);
                }
            }
        }

        if (type == CronFieldType.DAY_OF_WEEK) {
            if (sub.equals("L")) {
                return FieldCheckResult.ok("last day of the week (Saturday)");
            }
            if (sub.endsWith("L")) {
                String dowStr = sub.substring(0, sub.length() - 1);
                try {
                    int d = type.parseValue(dowStr);
                    if (d < 0 || d > 7) {
                        return FieldCheckResult.fail("Invalid day of week for 'L': " + dowStr);
                    }
                    return FieldCheckResult.ok("last " + dowStr + " of the month");
                } catch (Exception e) {
                    return FieldCheckResult.fail("Invalid 'L' expression: " + sub);
                }
            }
            if (sub.contains("#")) {
                String[] hashParts = sub.split("#");
                if (hashParts.length != 2) {
                    return FieldCheckResult.fail("Invalid '#' format in day of week: " + sub);
                }
                try {
                    int dow = type.parseValue(hashParts[0]);
                    int nth = Integer.parseInt(hashParts[1]);
                    if (dow < 0 || dow > 7) {
                        return FieldCheckResult.fail("Day of week must be 0-7: " + hashParts[0]);
                    }
                    if (nth < 1 || nth > 5) {
                        return FieldCheckResult.fail("Week number must be 1-5: " + hashParts[1]);
                    }
                    return FieldCheckResult.ok("the " + nth + "th occurrence of weekday " + hashParts[0]);
                } catch (Exception e) {
                    return FieldCheckResult.fail("Invalid '#' expression: " + sub);
                }
            }
        }

        // Step notation: /
        if (sub.contains("/")) {
            String[] stepParts = sub.split("/");
            if (stepParts.length != 2) {
                return FieldCheckResult.fail("Invalid step format: " + sub);
            }
            int step;
            try {
                step = Integer.parseInt(stepParts[1]);
                if (step <= 0) {
                    return FieldCheckResult.fail("Step value must be positive: " + stepParts[1]);
                }
            } catch (NumberFormatException e) {
                return FieldCheckResult.fail("Invalid step value: " + stepParts[1]);
            }

            String target = stepParts[0];
            if (target.equals("*")) {
                return FieldCheckResult.ok("every " + step + " " + type.getLabel() + "s");
            }
            // range or starting value
            FieldCheckResult targetCheck = checkSubExpression(type, target);
            if (!targetCheck.valid()) {
                return targetCheck;
            }
            return FieldCheckResult.ok(targetCheck.description() + " every " + step + " " + type.getLabel() + "s");
        }

        // Range notation: -
        if (sub.contains("-")) {
            String[] rangeParts = sub.split("-");
            if (rangeParts.length != 2) {
                return FieldCheckResult.fail("Invalid range format: " + sub);
            }
            int from;
            int to;
            try {
                from = type.parseValue(rangeParts[0]);
                to = type.parseValue(rangeParts[1]);
            } catch (Exception e) {
                return FieldCheckResult.fail("Invalid range values: " + sub);
            }

            if (from < type.getMinValue() || from > type.getMaxValue()) {
                return FieldCheckResult.fail("Range start value out of bounds: " + from);
            }
            if (to < type.getMinValue() || to > type.getMaxValue()) {
                return FieldCheckResult.fail("Range end value out of bounds: " + to);
            }
            if (from > to) {
                return FieldCheckResult.fail("Range start must be <= end: " + from + " > " + to);
            }
            return FieldCheckResult.ok("between " + type.getLabel() + " " + from + " and " + to);
        }

        // Single value
        try {
            int val = type.parseValue(sub);
            if (val < type.getMinValue() || val > type.getMaxValue()) {
                return FieldCheckResult.fail(
                        "Value " + val + " out of bounds (" + type.getMinValue() + "-" + type.getMaxValue() + ")"
                );
            }
            return FieldCheckResult.ok("at " + type.getLabel() + " " + val);
        } catch (Exception e) {
            return FieldCheckResult.fail("Unrecognized token: " + sub);
        }
    }
}
