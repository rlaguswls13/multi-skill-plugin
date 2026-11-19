package com.msp.cronhist;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Types of performance and operational anomalies in cron/scheduled executions.
 */
public enum AnomalyType {
    SEVERE_DRIFT,
    EXECUTION_OVERRUN,
    FAILURE_SPIKE
}
