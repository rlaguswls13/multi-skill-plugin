package com.msp.anycastprobe;

import java.time.Duration;
import java.util.Objects;

/**
 * Configuration for IP Anycast socket latency probing.
 *
 * @param samplesPerEndpoint number of socket connection attempts per endpoint
 * @param timeout            per-connection handshake timeout
 * @param interval           pause interval between consecutive samples
 * @param warmup             whether to perform an initial warm-up probe that is discarded from stats
 */
public record ProbeConfig(
        int samplesPerEndpoint,
        Duration timeout,
        Duration interval,
        boolean warmup
) {

    public ProbeConfig {
        if (samplesPerEndpoint <= 0) {
            throw new IllegalArgumentException("samplesPerEndpoint must be positive: " + samplesPerEndpoint);
        }
        Objects.requireNonNull(timeout, "timeout must not be null");
        Objects.requireNonNull(interval, "interval must not be null");
    }

    public static ProbeConfig defaultConfig() {
        return new ProbeConfig(5, Duration.ofSeconds(2), Duration.ofMillis(20), true);
    }

    public static ProbeConfig fastConfig() {
        return new ProbeConfig(3, Duration.ofSeconds(1), Duration.ZERO, false);
    }
}
