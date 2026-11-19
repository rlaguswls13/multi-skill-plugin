package com.msp.cronsla;

import java.time.Instant;
import java.util.Objects;

/**
 * Actionable remediation trigger produced by evaluating a {@link CatchUpPolicy} on missed schedule slots.
 */
public record CatchUpTrigger(
        String jobId,
        TriggerAction action,
        Instant targetSlotTime,
        String reason
) {
    public enum TriggerAction {
        EXECUTE_CATCH_UP,
        SKIP_MISSED_SLOT,
        EMIT_SLA_ALERT
    }

    public CatchUpTrigger {
        Objects.requireNonNull(jobId, "jobId must not be null");
        Objects.requireNonNull(action, "action must not be null");
        Objects.requireNonNull(targetSlotTime, "targetSlotTime must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
    }
}
