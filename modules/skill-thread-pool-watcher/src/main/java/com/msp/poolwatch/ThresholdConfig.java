package com.msp.poolwatch;

/**
 * Alert threshold configuration for queue saturation and worker utilization.
 *
 * @param queueSaturationThreshold fraction between 0.0 and 1.0 (e.g. 0.80 = 80%)
 * @param activeThreadThreshold    fraction between 0.0 and 1.0 (e.g. 0.90 = 90%)
 */
public record ThresholdConfig(
        double queueSaturationThreshold,
        double activeThreadThreshold
) {
    public static final ThresholdConfig DEFAULT = new ThresholdConfig(0.80, 0.90);

    public ThresholdConfig {
        if (queueSaturationThreshold < 0.0 || queueSaturationThreshold > 1.0) {
            throw new IllegalArgumentException("queueSaturationThreshold must be between 0.0 and 1.0: " + queueSaturationThreshold);
        }
        if (activeThreadThreshold < 0.0 || activeThreadThreshold > 1.0) {
            throw new IllegalArgumentException("activeThreadThreshold must be between 0.0 and 1.0: " + activeThreadThreshold);
        }
    }
}
