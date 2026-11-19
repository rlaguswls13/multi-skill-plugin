package com.msp.dpop;

import java.util.Optional;

/**
 * Outcome of DPoP proof validation.
 */
public record DpopValidationResult(
        boolean isValid,
        String thumbprint,
        String failureReason,
        DpopClaims claims
) {
    public static DpopValidationResult success(String thumbprint, DpopClaims claims) {
        return new DpopValidationResult(true, thumbprint, null, claims);
    }

    public static DpopValidationResult failure(String failureReason) {
        return new DpopValidationResult(false, null, failureReason, null);
    }

    public Optional<String> getThumbprint() {
        return Optional.ofNullable(thumbprint);
    }

    public Optional<String> getFailureReason() {
        return Optional.ofNullable(failureReason);
    }

    public Optional<DpopClaims> getClaims() {
        return Optional.ofNullable(claims);
    }
}
