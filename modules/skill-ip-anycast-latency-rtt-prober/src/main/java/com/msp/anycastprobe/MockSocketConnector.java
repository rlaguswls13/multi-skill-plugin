package com.msp.anycastprobe;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Mock SocketConnector simulating latencies, jitter, and packet loss for unit testing.
 */
public class MockSocketConnector implements SocketConnector {

    public record SimulationRule(Duration baseLatency, Duration jitterRange, double lossRate) {}

    private final Map<String, SimulationRule> rules = new ConcurrentHashMap<>();
    private SimulationRule defaultRule = new SimulationRule(Duration.ofMillis(20), Duration.ofMillis(5), 0.0);

    public MockSocketConnector setDefaultRule(Duration baseLatency, Duration jitterRange, double lossRate) {
        this.defaultRule = new SimulationRule(baseLatency, jitterRange, lossRate);
        return this;
    }

    public MockSocketConnector addRule(String host, int port, Duration baseLatency, Duration jitterRange, double lossRate) {
        rules.put(host + ":" + port, new SimulationRule(baseLatency, jitterRange, lossRate));
        return this;
    }

    @Override
    public Duration measureConnectRtt(String host, int port, Duration timeout) throws IOException {
        SimulationRule rule = rules.getOrDefault(host + ":" + port, defaultRule);

        // Simulate packet loss
        if (rule.lossRate() > 0.0 && ThreadLocalRandom.current().nextDouble() < rule.lossRate()) {
            throw new SocketTimeoutException("Connection timed out to " + host + ":" + port);
        }

        long baseNanos = rule.baseLatency().toNanos();
        long jitterSpan = rule.jitterRange().toNanos();
        long jitterOffset = jitterSpan > 0
                ? ThreadLocalRandom.current().nextLong(-jitterSpan, jitterSpan + 1)
                : 0L;

        long totalNanos = Math.max(100_000L, baseNanos + jitterOffset); // at least 0.1ms
        if (Duration.ofNanos(totalNanos).compareTo(timeout) > 0) {
            throw new SocketTimeoutException("Connect latency exceeded timeout of " + timeout);
        }

        return Duration.ofNanos(totalNanos);
    }
}
