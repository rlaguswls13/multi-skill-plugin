package com.msp.secheaders;

/**
 * Cross-Origin-Opener-Policy (COOP) header values.
 */
public enum CoopPolicy {
    SAME_ORIGIN("same-origin"),
    SAME_ORIGIN_ALLOW_POPUPS("same-origin-allow-popups"),
    UNSAFE_NONE("unsafe-none");

    private final String headerValue;

    CoopPolicy(String headerValue) {
        this.headerValue = headerValue;
    }

    public String headerValue() {
        return headerValue;
    }
}
