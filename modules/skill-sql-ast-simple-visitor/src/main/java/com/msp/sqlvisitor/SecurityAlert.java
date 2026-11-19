package com.msp.sqlvisitor;

/**
 * Security alert detected by the SQL AST security guard visitor.
 */
public record SecurityAlert(
        Severity severity,
        String ruleId,
        String message,
        SqlNode offendingNode
) {
    public enum Severity {
        HIGH,
        MEDIUM,
        LOW
    }
}
