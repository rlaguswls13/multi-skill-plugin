package com.msp.secheaders;

/**
 * Cross-Origin-Embedder-Policy (COEP) header values.
 */
public enum CoepPolicy {
    REQUIRE_CORP("require-corp"),
    CREDENTIALLESS("credentialless"),
    UNSAFE_NONE("unsafe-none");

    private final String headerValue;

    CoepPolicy(String headerValue) {
        this.headerValue = headerValue;
    }

    public String headerValue() {
        return headerValue;
    }
}
