package com.msp.secheaders;

/**
 * Cross-Origin-Resource-Policy (CORP) header values.
 */
public enum CorpPolicy {
    SAME_ORIGIN("same-origin"),
    SAME_SITE("same-site"),
    CROSS_ORIGIN("cross-origin");

    private final String headerValue;

    CorpPolicy(String headerValue) {
        this.headerValue = headerValue;
    }

    public String headerValue() {
        return headerValue;
    }
}
