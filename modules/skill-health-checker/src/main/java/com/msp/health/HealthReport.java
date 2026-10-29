package com.msp.health;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Aggregated health report containing overall status, total execution duration, and individual component results.
 */
public record HealthReport(
        HealthStatus overallStatus,
        Map<String, HealthResult> components,
        Duration totalDuration,
        Instant timestamp
) {
    public HealthReport {
        Objects.requireNonNull(overallStatus, "overallStatus must not be null");
        components = components != null ? Collections.unmodifiableMap(new TreeMap<>(components)) : Map.of();
        totalDuration = totalDuration != null ? totalDuration : Duration.ZERO;
        timestamp = timestamp != null ? timestamp : Instant.now();
    }

    public boolean isHealthy() {
        return overallStatus == HealthStatus.UP;
    }

    public boolean isOperational() {
        return overallStatus.isOperational();
    }

    /**
     * Converts this report to a JSON string.
     */
    public String toJson() {
        return toJson(false);
    }

    /**
     * Converts this report to a JSON string with optional pretty printing.
     */
    public String toJson(boolean pretty) {
        StringBuilder sb = new StringBuilder();
        String indent = pretty ? "  " : "";
        String nl = pretty ? "\n" : "";

        sb.append("{").append(nl);
        sb.append(indent).append("\"status\": \"").append(overallStatus).append("\",").append(nl);
        sb.append(indent).append("\"timestamp\": \"").append(timestamp).append("\",").append(nl);
        sb.append(indent).append("\"durationMs\": ").append(totalDuration.toMillis()).append(",").append(nl);
        sb.append(indent).append("\"components\": {").append(nl);

        int compIndex = 0;
        int compSize = components.size();
        for (Map.Entry<String, HealthResult> entry : components.entrySet()) {
            String name = entry.getKey();
            HealthResult res = entry.getValue();
            String compIndent = pretty ? "    " : "";
            String detailIndent = pretty ? "      " : "";

            sb.append(compIndent).append("\"").append(escapeJson(name)).append("\": {").append(nl);
            sb.append(detailIndent).append("\"status\": \"").append(res.status()).append("\",").append(nl);
            sb.append(detailIndent).append("\"message\": \"").append(escapeJson(res.message())).append("\",").append(nl);
            sb.append(detailIndent).append("\"durationMs\": ").append(res.duration().toMillis()).append(",").append(nl);
            sb.append(detailIndent).append("\"details\": {");

            if (!res.details().isEmpty()) {
                sb.append(nl);
                int detIndex = 0;
                int detSize = res.details().size();
                for (Map.Entry<String, Object> det : res.details().entrySet()) {
                    sb.append(detailIndent).append(pretty ? "  " : "")
                            .append("\"").append(escapeJson(det.getKey())).append("\": ")
                            .append(formatValue(det.getValue()));
                    if (++detIndex < detSize) {
                        sb.append(",");
                    }
                    sb.append(nl);
                }
                sb.append(detailIndent).append("}");
            } else {
                sb.append("}");
            }
            sb.append(nl);

            sb.append(compIndent).append("}");
            if (++compIndex < compSize) {
                sb.append(",");
            }
            sb.append(nl);
        }

        sb.append(indent).append("}").append(nl);
        sb.append("}");
        return sb.toString();
    }

    private static String formatValue(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        return "\"" + escapeJson(value.toString()) + "\"";
    }

    private static String escapeJson(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 32) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}
