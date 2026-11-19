package com.msp.cronsla;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Comprehensive SLA evaluation report for a scheduled job across an evaluated window.
 */
public record SlaEvaluationReport(
        String jobId,
        JobSlaPolicy policy,
        int totalExpectedSlots,
        List<SlotOccurrence> allOccurrences,
        List<SlotOccurrence> onTimeOccurrences,
        List<SlotOccurrence> lateOccurrences,
        List<SlotOccurrence> missedOccurrences,
        double slaComplianceRatePercent,
        List<CatchUpTrigger> catchUpTriggers,
        Instant nextScheduledSlot
) {
    public SlaEvaluationReport {
        Objects.requireNonNull(jobId, "jobId must not be null");
        Objects.requireNonNull(policy, "policy must not be null");
        allOccurrences = List.copyOf(allOccurrences);
        onTimeOccurrences = List.copyOf(onTimeOccurrences);
        lateOccurrences = List.copyOf(lateOccurrences);
        missedOccurrences = List.copyOf(missedOccurrences);
        catchUpTriggers = List.copyOf(catchUpTriggers);
    }

    public boolean isFullyCompliant() {
        return lateOccurrences.isEmpty() && missedOccurrences.isEmpty();
    }
}
