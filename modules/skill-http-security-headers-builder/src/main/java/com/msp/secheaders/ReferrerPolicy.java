package com.msp.secheaders;

/**
 * Referrer-Policy header values.
 * W3C Referrer Policy Recommendation.
 */
public enum ReferrerPolicy {
    NO_REFERRER("no-referrer"),
    NO_REFERRER_WHEN_DOWNGRADE("no-referrer-when-downgrade"),
    ORIGIN("origin"),
    ORIGIN_WHEN_CROSS_ORIGIN("origin-when-cross-origin"),
    SAME_ORIGIN("same-origin"),
    STRICT_ORIGIN("strict-origin"),
    STRICT_ORIGIN_WHEN_CROSS_ORIGIN("strict-origin-when-cross-origin"),
    UNSAFE_URL("unsafe-url");

    private final String headerValue;

    ReferrerPolicy(String headerValue) {
        this.headerValue = headerValue;
    }

    public String headerValue() {
        return headerValue;
    }
}
