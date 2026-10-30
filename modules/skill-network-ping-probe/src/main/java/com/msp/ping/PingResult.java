package com.msp.ping;

/**
 * Result of a network ping probe operation.
 *
 * @param host         target hostname or IP address
 * @param port         target port number (0 if ICMP probe)
 * @param reachable    true if host responded within timeout
 * @param rttMillis    round-trip time in milliseconds
 * @param errorMessage error description if ping failed, or null if successful
 */
public record PingResult(String host, int port, boolean reachable, long rttMillis, String errorMessage) {

    public static PingResult success(String host, int port, long rttMillis) {
        return new PingResult(host, port, true, Math.max(0, rttMillis), null);
    }

    public static PingResult failure(String host, int port, long rttMillis, String errorMessage) {
        return new PingResult(host, port, false, Math.max(0, rttMillis), errorMessage);
    }
}
