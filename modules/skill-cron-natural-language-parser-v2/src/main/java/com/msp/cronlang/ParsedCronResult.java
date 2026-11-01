package com.msp.cronlang;

import java.util.Objects;

/**
 * Result of parsing a natural language expression into Cron format.
 */
public record ParsedCronResult(
        String standardExpression,
        String quartzExpression,
        String humanDescription,
        String nextExecutionHint
) {
    public ParsedCronResult {
        Objects.requireNonNull(standardExpression, "standardExpression cannot be null");
        Objects.requireNonNull(quartzExpression, "quartzExpression cannot be null");
        Objects.requireNonNull(humanDescription, "humanDescription cannot be null");
    }

    public String expression() {
        return standardExpression;
    }

    public String expression(CronType type) {
        return type == CronType.QUARTZ_6_FIELD ? quartzExpression : standardExpression;
    }
}
