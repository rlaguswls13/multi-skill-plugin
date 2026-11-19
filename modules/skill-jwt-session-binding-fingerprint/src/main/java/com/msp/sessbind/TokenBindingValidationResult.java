package com.msp.sessbind;

/**
 * Result record of client session binding token verification.
 *
 * @param isValid      True if client request matches token binding proof
 * @param mismatchType Exact category of mismatch encountered
 * @param severity     Severity assessment for security monitoring/SOC alerts
 * @param expectedHash Hash recorded in JWT claims
 * @param actualHash   Hash computed from current client request
 * @param details      Human-readable explanation of verification outcome
 */
public record TokenBindingValidationResult(
        boolean isValid,
        MismatchType mismatchType,
        MismatchSeverity severity,
        String expectedHash,
        String actualHash,
        String details
) {
    public static TokenBindingValidationResult success(String hash) {
        return new TokenBindingValidationResult(
                true,
                MismatchType.NONE,
                MismatchSeverity.NONE,
                hash,
                hash,
                "Token binding verified successfully"
        );
    }

    public static TokenBindingValidationResult failure(
            MismatchType type,
            MismatchSeverity severity,
            String expectedHash,
            String actualHash,
            String details
    ) {
        return new TokenBindingValidationResult(
                false,
                type,
                severity,
                expectedHash,
                actualHash,
                details
        );
    }
}
