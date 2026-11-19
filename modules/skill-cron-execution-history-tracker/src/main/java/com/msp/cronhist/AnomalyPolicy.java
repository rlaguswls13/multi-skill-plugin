package com.msp.cronhist;

import java.time.Duration;

/**
 * Threshold policy for anomaly detection on scheduled tasks.
 */
public record AnomalyPolicy(
        Duration maxAllowedDrift,
        Duration expectedInterval,
        Duration maxAllowedDuration,
        int consecutiveFailureThreshold
) {
    public static final Duration DEFAULT_MAX_DRIFT = Duration.ofSeconds(5);
    public static final int DEFAULT_CONSECUTIVE_FAILURES = 3;

    public static Builder builder() {
        return new Builder();
    }

    public static AnomalyPolicy standard() {
        return builder()
                .maxAllowedDrift(DEFAULT_MAX_DRIFT)
                .consecutiveFailureThreshold(DEFAULT_CONSECUTIVE_FAILURES)
                .build();
    }

    public static final class Builder {
        private Duration maxAllowedDrift = DEFAULT_MAX_DRIFT;
        private Duration expectedInterval;
        private Duration maxAllowedDuration;
        private int consecutiveFailureThreshold = DEFAULT_CONSECUTIVE_FAILURES;

        public Builder maxAllowedDrift(Duration drift) {
            this.maxAllowedDrift = drift;
            return this;
        }

        public Builder expectedInterval(Duration interval) {
            this.expectedInterval = interval;
            return this;
        }

        public Builder maxAllowedDuration(Duration duration) {
            this.maxAllowedDuration = duration;
            return this;
        }

        public Builder consecutiveFailureThreshold(int count) {
            this.consecutiveFailureThreshold = count;
            return this;
        }

        public AnomalyPolicy build() {
            return new AnomalyPolicy(maxAllowedDrift, expectedInterval, maxAllowedDuration, consecutiveFailureThreshold);
        }
    }
}
