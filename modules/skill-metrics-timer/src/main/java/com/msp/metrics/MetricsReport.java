package com.msp.metrics;

import java.time.Instant;
import java.util.*;

/**
 * Formatted summary report of collected performance metrics.
 * Supports tabular console/log output and zero-dependency JSON serialization.
 */
public class MetricsReport {

    private final Instant generatedAt;
    private final List<MetricSnapshot> snapshots;

    public MetricsReport(Collection<MetricSnapshot> snapshots) {
        this.generatedAt = Instant.now();
        List<MetricSnapshot> list = new ArrayList<>(snapshots);
        list.sort(Comparator.comparing(MetricSnapshot::name));
        this.snapshots = Collections.unmodifiableList(list);
    }

    public Instant generatedAt() {
        return generatedAt;
    }

    public List<MetricSnapshot> snapshots() {
        return snapshots;
    }

    /**
     * Formats the collected metrics into a human-readable tabular ASCII string.
     */
    public String toTable() {
        if (snapshots.isEmpty()) {
            return "No metrics recorded at " + generatedAt;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("=== Metrics Report [%s] ===%n", generatedAt));
        sb.append(String.format("%-28s %10s %10s %10s %10s %10s %10s %10s %10s%n",
                "Metric Name", "Count", "Min(ms)", "Max(ms)", "Avg(ms)", "P50(ms)", "P90(ms)", "P99(ms)", "TPS"));
        sb.append("-".repeat(116)).append("\n");

        for (MetricSnapshot s : snapshots) {
            sb.append(String.format("%-28s %10d %10.2f %10.2f %10.2f %10.2f %10.2f %10.2f %10.1f%n",
                    s.name(),
                    s.count(),
                    s.minMillis(),
                    s.maxMillis(),
                    s.meanMillis(),
                    s.p50Millis(),
                    s.p90Millis(),
                    s.p99Millis(),
                    s.tps()));
        }
        sb.append("-".repeat(116)).append("\n");
        return sb.toString();
    }

    /**
     * Serializes the report into standard JSON format without external JSON library dependencies.
     */
    public String toJson() {
        StringBuilder sb = new StringBuilder(256 + snapshots.size() * 128);
        sb.append("{");
        sb.append("\"generatedAt\":\"").append(generatedAt.toString()).append("\",");
        sb.append("\"metrics\":[");

        for (int i = 0; i < snapshots.size(); i++) {
            if (i > 0) sb.append(",");
            MetricSnapshot s = snapshots.get(i);
            sb.append("{");
            sb.append("\"name\":\"").append(escapeJson(s.name())).append("\",");
            sb.append("\"count\":").append(s.count()).append(",");
            sb.append("\"minMs\":").append(String.format(Locale.ROOT, "%.3f", s.minMillis())).append(",");
            sb.append("\"maxMs\":").append(String.format(Locale.ROOT, "%.3f", s.maxMillis())).append(",");
            sb.append("\"avgMs\":").append(String.format(Locale.ROOT, "%.3f", s.meanMillis())).append(",");
            sb.append("\"p50Ms\":").append(String.format(Locale.ROOT, "%.3f", s.p50Millis())).append(",");
            sb.append("\"p90Ms\":").append(String.format(Locale.ROOT, "%.3f", s.p90Millis())).append(",");
            sb.append("\"p95Ms\":").append(String.format(Locale.ROOT, "%.3f", s.p95Millis())).append(",");
            sb.append("\"p99Ms\":").append(String.format(Locale.ROOT, "%.3f", s.p99Millis())).append(",");
            sb.append("\"tps\":").append(String.format(Locale.ROOT, "%.2f", s.tps()));
            sb.append("}");
        }

        sb.append("]}");
        return sb.toString();
    }

    @Override
    public String toString() {
        return toTable();
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\"", "\\\"");
    }
}
