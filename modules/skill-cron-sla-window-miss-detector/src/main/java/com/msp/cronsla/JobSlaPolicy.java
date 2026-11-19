package com.msp.cronsla;

import java.time.Duration;
import java.util.Objects;

/**
 * SLA Policy configuration for a scheduled job.
 *
 * @param jobId            unique identifier of the scheduled job
 * @param expectedInterval nominal duration between consecutive scheduled runs
 * @param slaGraceWindow   maximum tolerated delay after {@code scheduledTime} before an execution is marked as SLA-breached (late)
 * @param catchUpPolicy    remediation strategy when one or more scheduled slots are missed
 */
public record JobSlaPolicy(
        String jobId,
        Duration expectedInterval,
        Duration slaGraceWindow,
        CatchUpPolicy catchUpPolicy
) {
    public JobSlaPolicy {
        Objects.requireNonNull(jobId, "jobId must not be null");
        if (jobId.isBlank()) {
            throw new IllegalArgumentException("jobId must not be blank");
        }
        Objects.requireNonNull(expectedInterval, "expectedInterval must not be null");
        if (expectedInterval.isZero() || expectedInterval.isNegative()) {
            throw new IllegalArgumentException("expectedInterval must be > 0, got: " + expectedInterval);
        }
        Objects.requireNonNull(slaGraceWindow, "slaGraceWindow must not be null");
        if (slaGraceWindow.isNegative()) {
            throw new IllegalArgumentException("slaGraceWindow must be >= 0, got: " + slaGraceWindow);
        }
        Objects.requireNonNull(catchUpPolicy, "catchUpPolicy must not be null");
    }
}
