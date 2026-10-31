package com.msp.cronvalidator;

import java.util.List;

/**
 * Result of validating and parsing a complete Cron expression.
 */
public record CronValidationResult(
        boolean valid,
        String expression,
        String errorMessage,
        List<CronFieldDetail> fields
) {
    public static CronValidationResult success(String expression, List<CronFieldDetail> fields) {
        return new CronValidationResult(true, expression, null, fields);
    }

    public static CronValidationResult error(String expression, String errorMessage) {
        return new CronValidationResult(false, expression, errorMessage, List.of());
    }

    public static CronValidationResult error(String expression, String errorMessage, List<CronFieldDetail> fields) {
        return new CronValidationResult(false, expression, errorMessage, fields);
    }
}
