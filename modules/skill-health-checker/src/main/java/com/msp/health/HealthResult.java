package com.msp.health;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Result of a single health check execution.
 */
public record HealthResult(
        HealthStatus status,
        String message,
        Map<String, Object> details,
        Duration duration,
        Instant timestamp
) {
    public HealthResult {
        Objects.requireNonNull(status, "status must not be null");
        message = message != null ? message : "";
        details = details != null ? Collections.unmodifiableMap(new LinkedHashMap<>(details)) : Map.of();
        duration = duration != null ? duration : Duration.ZERO;
        timestamp = timestamp != null ? timestamp : Instant.now();
    }

    public static HealthResult up() {
        return up("OK", Map.of(), Duration.ZERO);
    }

    public static HealthResult up(String message) {
        return up(message, Map.of(), Duration.ZERO);
    }

    public static HealthResult up(String message, Map<String, Object> details, Duration duration) {
        return new HealthResult(HealthStatus.UP, message, details, duration, Instant.now());
    }

    public static HealthResult down(String message) {
        return down(message, Map.of(), Duration.ZERO);
    }

    public static HealthResult down(String message, Throwable cause) {
        Map<String, Object> details = new LinkedHashMap<>();
        if (cause != null) {
            details.put("errorType", cause.getClass().getName());
            details.put("errorMessage", cause.getMessage() != null ? cause.getMessage() : "");
        }
        return down(message, details, Duration.ZERO);
    }

    public static HealthResult down(String message, Map<String, Object> details, Duration duration) {
        return new HealthResult(HealthStatus.DOWN, message, details, duration, Instant.now());
    }

    public static HealthResult degraded(String message) {
        return degraded(message, Map.of(), Duration.ZERO);
    }

    public static HealthResult degraded(String message, Map<String, Object> details, Duration duration) {
        return new HealthResult(HealthStatus.DEGRADED, message, details, duration, Instant.now());
    }

    public static HealthResult unknown(String message) {
        return new HealthResult(HealthStatus.UNKNOWN, message, Map.of(), Duration.ZERO, Instant.now());
    }
}
