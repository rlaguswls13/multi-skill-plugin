package com.msp.health;

/**
 * Health status representing the operational readiness of a component or system.
 */
public enum HealthStatus {
    UP(1),
    UNKNOWN(2),
    DEGRADED(3),
    DOWN(4);

    private final int severity;

    HealthStatus(int severity) {
        this.severity = severity;
    }

    public int getSeverity() {
        return severity;
    }

    /**
     * Combines this status with another, returning the one with higher severity.
     */
    public HealthStatus combine(HealthStatus other) {
        if (other == null) {
            return this;
        }
        return this.severity >= other.severity ? this : other;
    }

    public boolean isHealthy() {
        return this == UP;
    }

    public boolean isOperational() {
        return this == UP || this == DEGRADED;
    }
}
