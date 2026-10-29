package com.msp.poolwatch;

/**
 * Listener invoked when a thread pool exceeds configured alert thresholds or rejects tasks.
 */
@FunctionalInterface
public interface ThresholdAlertListener {
    /**
     * Called when a monitored thread pool threshold is exceeded.
     *
     * @param snapshot       current point-in-time pool snapshot
     * @param reason         the triggered threshold reason
     * @param currentValue   the measured metric value
     * @param thresholdValue the configured limit
     */
    void onThresholdExceeded(PoolSnapshot snapshot, AlertReason reason, double currentValue, double thresholdValue);
}
