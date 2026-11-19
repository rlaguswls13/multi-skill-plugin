package com.msp.anycastprobe;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Aggregated latency statistics, jitter, and packet loss metrics for an Endpoint.
 *
 * @param endpoint      target endpoint probed
 * @param totalSamples  total probe attempts (excluding warmup)
 * @param successCount  successful handshake count
 * @param failureCount  timed out or failed handshake count
 * @param lossRate      fraction of failed handshakes (0.0 to 1.0)
 * @param minRtt        minimum round-trip time among successful handshakes
 * @param maxRtt        maximum round-trip time among successful handshakes
 * @param meanRtt       arithmetic mean of round-trip times
 * @param medianRtt     median round-trip time
 * @param jitter        standard deviation of round-trip times
 * @param score         composite score (lower is better; factors mean latency, jitter, and loss)
 */
public record ProbeStatistics(
        Endpoint endpoint,
        int totalSamples,
        int successCount,
        int failureCount,
        double lossRate,
        Duration minRtt,
        Duration maxRtt,
        Duration meanRtt,
        Duration medianRtt,
        Duration jitter,
        double score
) implements Comparable<ProbeStatistics> {

    public ProbeStatistics {
        Objects.requireNonNull(endpoint, "endpoint must not be null");
    }

    public static ProbeStatistics compute(Endpoint endpoint, List<ProbeSample> samples) {
        if (samples == null || samples.isEmpty()) {
            return new ProbeStatistics(
                    endpoint, 0, 0, 0, 1.0,
                    Duration.ZERO, Duration.ZERO, Duration.ZERO, Duration.ZERO, Duration.ZERO,
                    Double.MAX_VALUE
            );
        }

        int total = samples.size();
        List<Long> nanos = new ArrayList<>();
        int failures = 0;

        for (ProbeSample s : samples) {
            if (s.success() && s.rtt() != null) {
                nanos.add(s.rtt().toNanos());
            } else {
                failures++;
            }
        }

        int successes = nanos.size();
        double lossRate = (double) failures / total;

        if (successes == 0) {
            return new ProbeStatistics(
                    endpoint, total, 0, failures, lossRate,
                    Duration.ZERO, Duration.ZERO, Duration.ZERO, Duration.ZERO, Duration.ZERO,
                    Double.MAX_VALUE
            );
        }

        Collections.sort(nanos);
        long min = nanos.get(0);
        long max = nanos.get(nanos.size() - 1);

        double sum = 0;
        for (long n : nanos) {
            sum += n;
        }
        double meanNanos = sum / successes;

        long medianNanos;
        if (successes % 2 == 1) {
            medianNanos = nanos.get(successes / 2);
        } else {
            medianNanos = (nanos.get(successes / 2 - 1) + nanos.get(successes / 2)) / 2;
        }

        // Standard deviation (Jitter)
        double varianceSum = 0;
        for (long n : nanos) {
            double diff = n - meanNanos;
            varianceSum += diff * diff;
        }
        double jitterNanos = Math.sqrt(varianceSum / successes);

        double meanMs = meanNanos / 1_000_000.0;
        double jitterMs = jitterNanos / 1_000_000.0;
        // Composite score penalizes high jitter and packet loss
        double score = meanMs + (jitterMs * 1.5) + (lossRate * 500.0);

        return new ProbeStatistics(
                endpoint,
                total,
                successes,
                failures,
                lossRate,
                Duration.ofNanos(min),
                Duration.ofNanos(max),
                Duration.ofNanos((long) meanNanos),
                Duration.ofNanos(medianNanos),
                Duration.ofNanos((long) jitterNanos),
                score
        );
    }

    @Override
    public int compareTo(ProbeStatistics other) {
        return Double.compare(this.score, other.score);
    }
}
