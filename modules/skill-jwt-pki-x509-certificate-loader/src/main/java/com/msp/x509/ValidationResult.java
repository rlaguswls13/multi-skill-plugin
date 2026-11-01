package com.msp.x509;

/**
 * Result of X.509 certificate chain validation.
 */
public record ValidationResult(
        boolean valid,
        String errorMessage,
        int invalidCertIndex
) {
    public static ValidationResult success() {
        return new ValidationResult(true, null, -1);
    }

    public static ValidationResult failure(String errorMessage, int invalidCertIndex) {
        return new ValidationResult(false, errorMessage, invalidCertIndex);
    }
}
