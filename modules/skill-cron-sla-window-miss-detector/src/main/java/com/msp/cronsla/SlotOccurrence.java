package com.msp.cronsla;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Evaluates a single expected schedule slot against actual execution timestamps.
 */
public record SlotOccurrence(
        Instant scheduledTime,
        Instant slaDeadline,
        Instant actualExecutionTime,
        SlotStatus status,
        Duration delay
) {
    public enum SlotStatus {
        /**
         * Execution started within {@code [scheduledTime, scheduledTime + slaGraceWindow]}.
         */
        ON_TIME,
        /**
         * Execution started before the next slot but after {@code scheduledTime + slaGraceWindow}.
         */
        LATE_SLA_BREACH,
        /**
         * No execution occurred within this slot window before the next slot or evaluation cutoff.
         */
        MISSED
    }

    public SlotOccurrence {
        Objects.requireNonNull(scheduledTime, "scheduledTime must not be null");
        Objects.requireNonNull(slaDeadline, "slaDeadline must not be null");
        Objects.requireNonNull(status, "status must not be null");
    }
}
