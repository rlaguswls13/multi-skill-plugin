package com.msp.leakdetect;

/**
 * Configuration parameters for detecting memory leak patterns in monitored collections.
 */
public record DetectionConfig(
        int consecutiveGrowthThreshold,
        double velocityThresholdPerSecond,
        int maxCapacityThreshold,
        int maxHistorySamples
) {
    public DetectionConfig {
        if (consecutiveGrowthThreshold < 2) {
            throw new IllegalArgumentException("consecutiveGrowthThreshold must be >= 2");
        }
        if (maxHistorySamples < 2) {
            throw new IllegalArgumentException("maxHistorySamples must be >= 2");
        }
    }

    public static DetectionConfig defaults() {
        return new DetectionConfig(5, 50.0, 100_000, 30);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private int consecutiveGrowthThreshold = 5;
        private double velocityThresholdPerSecond = 50.0;
        private int maxCapacityThreshold = 100_000;
        private int maxHistorySamples = 30;

        public Builder consecutiveGrowthThreshold(int threshold) {
            this.consecutiveGrowthThreshold = threshold;
            return this;
        }

        public Builder velocityThresholdPerSecond(double velocity) {
            this.velocityThresholdPerSecond = velocity;
            return this;
        }

        public Builder maxCapacityThreshold(int maxCapacity) {
            this.maxCapacityThreshold = maxCapacity;
            return this;
        }

        public Builder maxHistorySamples(int maxSamples) {
            this.maxHistorySamples = maxSamples;
            return this;
        }

        public DetectionConfig build() {
            return new DetectionConfig(
                    consecutiveGrowthThreshold,
                    velocityThresholdPerSecond,
                    maxCapacityThreshold,
                    maxHistorySamples
            );
        }
    }
}
