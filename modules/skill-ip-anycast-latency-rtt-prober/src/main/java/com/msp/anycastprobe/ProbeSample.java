package com.msp.anycastprobe;

import java.time.Duration;
import java.time.Instant;

/**
 * Result of a single TCP handshake probe attempt.
 *
 * @param rtt          measured round-trip time (null if failed)
 * @param success      true if TCP handshake succeeded within timeout
 * @param errorMessage error description if connection failed
 * @param timestamp    timestamp of the measurement
 */
public record ProbeSample(
        Duration rtt,
        boolean success,
        String errorMessage,
        Instant timestamp
) {

    public static ProbeSample success(Duration rtt) {
        return new ProbeSample(rtt, true, null, Instant.now());
    }

    public static ProbeSample failure(String errorMessage) {
        return new ProbeSample(null, false, errorMessage, Instant.now());
    }
}
