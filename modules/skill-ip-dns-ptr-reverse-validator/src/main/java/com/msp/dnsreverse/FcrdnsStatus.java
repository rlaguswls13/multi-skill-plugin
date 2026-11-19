package com.msp.dnsreverse;

/**
 * Outcome status of Forward-Confirmed Reverse DNS (FCrDNS) validation.
 */
public enum FcrdnsStatus {
    /**
     * FCrDNS validation successfully passed: PTR domain forward resolves to client IP.
     */
    CONFIRMED,

    /**
     * IP address does not have a PTR reverse DNS record.
     */
    NO_PTR_RECORD,

    /**
     * PTR record exists, but forward DNS resolution of that domain failed.
     */
    FORWARD_LOOKUP_FAILED,

    /**
     * PTR record exists, but forward DNS resolution does not contain the client IP (anti-spoofing triggered).
     */
    IP_MISMATCH,

    /**
     * DNS lookup error occurred.
     */
    DNS_ERROR
}
