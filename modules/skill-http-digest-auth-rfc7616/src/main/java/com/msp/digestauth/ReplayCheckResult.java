package com.msp.digestauth;

/**
 * Result of validating a Digest {@code nonce} and {@code nc} (nonce-count) against replay attacks.
 */
public enum ReplayCheckResult {
    VALID,
    UNKNOWN_NONCE,
    EXPIRED_NONCE,
    INVALID_NC,
    REPLAYED_NC
}
