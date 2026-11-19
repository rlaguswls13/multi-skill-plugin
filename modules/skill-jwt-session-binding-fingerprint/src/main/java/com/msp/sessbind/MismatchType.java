package com.msp.sessbind;

/**
 * Types of session binding mismatches during token verification.
 */
public enum MismatchType {
    NONE,
    MISSING_BINDING_CLAIM,
    IP_SUBNET_MISMATCH,
    USER_AGENT_MISMATCH,
    TLS_CHANNEL_MISMATCH,
    DEVICE_KEY_MISMATCH,
    HASH_MISMATCH
}
