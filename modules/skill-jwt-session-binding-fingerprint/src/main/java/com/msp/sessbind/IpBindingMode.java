package com.msp.sessbind;

/**
 * Strategy for binding client IP address into session token.
 */
public enum IpBindingMode {
    /**
     * Bind exact IP address (e.g. 192.168.1.10).
     */
    EXACT,

    /**
     * Bind IP subnet (/24 for IPv4, /64 for IPv6) to tolerate minor mobile roaming/tethering.
     */
    SUBNET,

    /**
     * Disable IP binding completely (e.g. for aggressive cellular IP rotation).
     */
    DISABLED
}
