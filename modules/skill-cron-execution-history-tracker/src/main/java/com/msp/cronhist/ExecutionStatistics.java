package com.msp.cronhist;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Rolling execution performance statistics for a scheduled task.
 */
public record ExecutionStatistics(
        String taskId,
        int sampleCount,
        int successCount,
        int failureCount,
        double successRate,
        Duration meanDrift,
        Duration minDrift,
        Duration maxDrift,
        Duration p50Drift,
        Duration p90Drift,
        Duration p95Drift,
        Duration p99Drift,
        Duration meanDuration,
        Duration minDuration,
        Duration maxDuration,
        Duration p50Duration,
        Duration p95Duration,
        Duration p99Duration,
        double durationStdDevMs
) {
    public static ExecutionStatistics fromRecords(String taskId, List<ExecutionRecord> records) {
        if (records == null || records.isEmpty()) {
            throw new IllegalArgumentException("Records list cannot be null or empty");
        }

        int count = records.size();
        int successes = 0;
        int failures = 0;

        List<Long> driftsMs = new ArrayList<>(count);
        List<Long> durationsMs = new ArrayList<>(count);

        long totalDriftMs = 0;
        long totalDurationMs = 0;

        for (ExecutionRecord r : records) {
            if (r.success()) {
                successes++;
            } else {
                failures++;
            }

            long driftMs = r.drift().toMillis();
            long durMs = r.duration().toMillis();

            driftsMs.add(driftMs);
            durationsMs.add(durMs);

            totalDriftMs += driftMs;
            totalDurationMs += durMs;
        }

        Collections.sort(driftsMs);
        Collections.sort(durationsMs);

        double meanDriftVal = (double) totalDriftMs / count;
        double meanDurVal = (double) totalDurationMs / count;

        // Calculate standard deviation of duration
        double sumSqDiff = 0.0;
        for (long d : durationsMs) {
            double diff = d - meanDurVal;
            sumSqDiff += diff * diff;
        }
        double stdDev = Math.sqrt(sumSqDiff / count);

        return new ExecutionStatistics(
                taskId,
                count,
                successes,
                failures,
                (double) successes / count,
                Duration.ofMillis((long) meanDriftVal),
                Duration.ofMillis(driftsMs.get(0)),
                Duration.ofMillis(driftsMs.get(count - 1)),
                Duration.ofMillis(percentile(driftsMs, 50)),
                Duration.ofMillis(percentile(driftsMs, 90)),
                Duration.ofMillis(percentile(driftsMs, 95)),
                Duration.ofMillis(percentile(driftsMs, 99)),
                Duration.ofMillis((long) meanDurVal),
                Duration.ofMillis(durationsMs.get(0)),
                Duration.ofMillis(durationsMs.get(count - 1)),
                Duration.ofMillis(percentile(durationsMs, 50)),
                Duration.ofMillis(percentile(durationsMs, 95)),
                Duration.ofMillis(percentile(durationsMs, 99)),
                stdDev
        );
    }

    private static long percentile(List<Long> sorted, double pct) {
        if (sorted.isEmpty()) return 0;
        if (sorted.size() == 1) return sorted.get(0);
        int idx = (int) Math.ceil((pct / 100.0) * sorted.size()) - 1;
        idx = Math.max(0, Math.min(sorted.size() - 1, idx));
        return sorted.get(idx);
    }
}
