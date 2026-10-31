package com.msp.ipreputation;

/**
 * Result of an IP reputation check.
 *
 * @param blocked whether the IP is blocked
 * @param matchedRule the CIDR or IP rule that matched (null if clean)
 * @param reason description or explanation for the check result
 */
public record ReputationCheckResult(
        boolean blocked,
        String matchedRule,
        String reason
) {
    public static ReputationCheckResult clean() {
        return new ReputationCheckResult(false, null, "Clean IP");
    }

    public static ReputationCheckResult allowed(String matchedRule, String reason) {
        return new ReputationCheckResult(false, matchedRule, reason != null ? reason : "Whitelisted");
    }

    public static ReputationCheckResult blocked(String matchedRule, String reason) {
        return new ReputationCheckResult(true, matchedRule, reason != null ? reason : "Blocked by rule");
    }
}
