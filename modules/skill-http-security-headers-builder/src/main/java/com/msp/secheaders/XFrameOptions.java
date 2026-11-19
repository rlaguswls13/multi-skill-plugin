package com.msp.secheaders;

/**
 * X-Frame-Options header values.
 */
public enum XFrameOptions {
    DENY("DENY"),
    SAMEORIGIN("SAMEORIGIN");

    private final String headerValue;

    XFrameOptions(String headerValue) {
        this.headerValue = headerValue;
    }

    public String headerValue() {
        return headerValue;
    }
}
