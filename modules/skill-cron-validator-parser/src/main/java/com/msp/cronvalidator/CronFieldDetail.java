package com.msp.cronvalidator;

/**
 * Detailed validation result and description for an individual Cron field.
 */
public record CronFieldDetail(
        CronFieldType type,
        String expression,
        String description,
        boolean valid,
        String errorMessage
) {
    public static CronFieldDetail valid(CronFieldType type, String expression, String description) {
        return new CronFieldDetail(type, expression, description, true, null);
    }

    public static CronFieldDetail invalid(CronFieldType type, String expression, String errorMessage) {
        return new CronFieldDetail(type, expression, null, false, errorMessage);
    }
}
