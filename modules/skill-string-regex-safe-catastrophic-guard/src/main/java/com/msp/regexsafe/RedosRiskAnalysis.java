package com.msp.regexsafe;

import java.util.List;
import java.util.Objects;

/**
 * Static vulnerability analysis result for a regular expression pattern.
 */
public record RedosRiskAnalysis(
        String pattern,
        RedosRiskLevel riskLevel,
        boolean isVulnerable,
        List<String> warnings
) {
    public RedosRiskAnalysis {
        Objects.requireNonNull(pattern, "pattern cannot be null");
        Objects.requireNonNull(riskLevel, "riskLevel cannot be null");
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public static RedosRiskAnalysis safe(String pattern) {
        return new RedosRiskAnalysis(pattern, RedosRiskLevel.SAFE, false, List.of());
    }
}
