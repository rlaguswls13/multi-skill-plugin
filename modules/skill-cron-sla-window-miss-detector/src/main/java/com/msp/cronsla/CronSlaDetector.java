package com.msp.cronsla;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe Scheduled Job SLA Window Breach &amp; Missed Execution Detector with Catch-up Policy Evaluator.
 */
public final class CronSlaDetector {

    private final Map<String, JobSlaPolicy> policies = new ConcurrentHashMap<>();

    /**
     * Registers or updates an SLA policy for a job.
     */
    public void registerPolicy(JobSlaPolicy policy) {
        Objects.requireNonNull(policy, "policy must not be null");
        policies.put(policy.jobId(), policy);
    }

    /**
     * Retrieves a registered SLA policy by job ID.
     */
    public JobSlaPolicy getPolicy(String jobId) {
        JobSlaPolicy policy = policies.get(jobId);
        if (policy == null) {
            throw new IllegalArgumentException("No registered SLA policy for jobId: " + jobId);
        }
        return policy;
    }

    /**
     * Generates a periodic sequence of expected schedule timestamps within {@code [windowStart, windowEnd]}.
     */
    public static List<Instant> generatePeriodicSchedule(
            Instant windowStart,
            Instant windowEnd,
            Duration interval) {
        Objects.requireNonNull(windowStart, "windowStart must not be null");
        Objects.requireNonNull(windowEnd, "windowEnd must not be null");
        Objects.requireNonNull(interval, "interval must not be null");
        if (interval.isZero() || interval.isNegative()) {
            throw new IllegalArgumentException("interval must be > 0");
        }
        if (windowEnd.isBefore(windowStart)) {
            throw new IllegalArgumentException("windowEnd must not be before windowStart");
        }
        List<Instant> slots = new ArrayList<>();
        Instant cursor = windowStart;
        while (!cursor.isAfter(windowEnd)) {
            slots.add(cursor);
            cursor = cursor.plus(interval);
        }
        return List.copyOf(slots);
    }

    /**
     * Evaluates a job's expected schedule timestamps against actual execution/heartbeat timestamps
     * as of {@code evaluationTime}.
     *
     * @param jobId                registered job ID
     * @param expectedSchedule     expected scheduled timestamps
     * @param actualExecutionTimes actual observed start/heartbeat timestamps
     * @param evaluationTime       current reference timestamp ("now")
     */
    public SlaEvaluationReport evaluate(
            String jobId,
            List<Instant> expectedSchedule,
            List<Instant> actualExecutionTimes,
            Instant evaluationTime) {
        JobSlaPolicy policy = getPolicy(jobId);
        return evaluateWithPolicy(policy, expectedSchedule, actualExecutionTimes, evaluationTime);
    }

    /**
     * Evaluates expected schedule timestamps against actual execution timestamps using the supplied {@link JobSlaPolicy}.
     */
    public SlaEvaluationReport evaluateWithPolicy(
            JobSlaPolicy policy,
            List<Instant> expectedSchedule,
            List<Instant> actualExecutionTimes,
            Instant evaluationTime) {
        Objects.requireNonNull(policy, "policy must not be null");
        Objects.requireNonNull(expectedSchedule, "expectedSchedule must not be null");
        Objects.requireNonNull(actualExecutionTimes, "actualExecutionTimes must not be null");
        Objects.requireNonNull(evaluationTime, "evaluationTime must not be null");

        List<Instant> sortedSchedule = expectedSchedule.stream()
                .filter(Objects::nonNull)
                .filter(t -> !t.isAfter(evaluationTime))
                .sorted()
                .toList();

        List<Instant> sortedActuals = new ArrayList<>(actualExecutionTimes.stream()
                .filter(Objects::nonNull)
                .filter(t -> !t.isAfter(evaluationTime))
                .sorted(Comparator.naturalOrder())
                .toList());

        List<SlotOccurrence> allOccurrences = new ArrayList<>(sortedSchedule.size());
        List<SlotOccurrence> onTime = new ArrayList<>();
        List<SlotOccurrence> late = new ArrayList<>();
        List<SlotOccurrence> missed = new ArrayList<>();

        int actualIdx = 0;
        for (int i = 0; i < sortedSchedule.size(); i++) {
            Instant slotStart = sortedSchedule.get(i);
            Instant slotDeadline = slotStart.plus(policy.slaGraceWindow());
            // A slot extends until the next scheduled slot (or evaluationTime if last slot)
            Instant slotWindowBound = (i + 1 < sortedSchedule.size())
                    ? sortedSchedule.get(i + 1)
                    : slotStart.plus(policy.expectedInterval());
            if (slotWindowBound.isAfter(evaluationTime)) {
                slotWindowBound = evaluationTime.plusNanos(1);
            }

            // Advance past any executions that occurred strictly before this slotStart
            while (actualIdx < sortedActuals.size() && sortedActuals.get(actualIdx).isBefore(slotStart)) {
                actualIdx++;
            }

            if (actualIdx < sortedActuals.size() && sortedActuals.get(actualIdx).isBefore(slotWindowBound)) {
                Instant execTime = sortedActuals.get(actualIdx);
                actualIdx++; // consume this execution for this slot
                Duration delay = Duration.between(slotStart, execTime);
                if (!execTime.isAfter(slotDeadline)) {
                    SlotOccurrence occ = new SlotOccurrence(
                            slotStart, slotDeadline, execTime, SlotOccurrence.SlotStatus.ON_TIME, delay);
                    allOccurrences.add(occ);
                    onTime.add(occ);
                } else {
                    SlotOccurrence occ = new SlotOccurrence(
                            slotStart, slotDeadline, execTime, SlotOccurrence.SlotStatus.LATE_SLA_BREACH, delay);
                    allOccurrences.add(occ);
                    late.add(occ);
                }
            } else {
                // Only count as MISSED if the SLA deadline has passed as of evaluationTime
                if (evaluationTime.isAfter(slotDeadline)) {
                    SlotOccurrence occ = new SlotOccurrence(
                            slotStart, slotDeadline, null, SlotOccurrence.SlotStatus.MISSED, null);
                    allOccurrences.add(occ);
                    missed.add(occ);
                }
            }
        }

        int totalEvaluated = allOccurrences.size();
        double complianceRate = totalEvaluated == 0
                ? 100.0
                : Math.round((onTime.size() * 10000.0) / totalEvaluated) / 100.0;

        Instant nextScheduled = sortedSchedule.isEmpty()
                ? evaluationTime.plus(policy.expectedInterval())
                : sortedSchedule.get(sortedSchedule.size() - 1).plus(policy.expectedInterval());

        List<CatchUpTrigger> triggers = buildCatchUpTriggers(policy, missed, late, nextScheduled);

        return new SlaEvaluationReport(
                policy.jobId(),
                policy,
                totalEvaluated,
                allOccurrences,
                onTime,
                late,
                missed,
                complianceRate,
                triggers,
                nextScheduled
        );
    }

    private static List<CatchUpTrigger> buildCatchUpTriggers(
            JobSlaPolicy policy,
            List<SlotOccurrence> missed,
            List<SlotOccurrence> late,
            Instant nextScheduled) {
        List<CatchUpTrigger> triggers = new ArrayList<>();
        String jobId = policy.jobId();

        if (!missed.isEmpty()) {
            switch (policy.catchUpPolicy()) {
                case FIRE_ONCE_IMMEDIATELY -> {
                    SlotOccurrence latestMissed = missed.get(missed.size() - 1);
                    for (int i = 0; i < missed.size() - 1; i++) {
                        triggers.add(new CatchUpTrigger(
                                jobId,
                                CatchUpTrigger.TriggerAction.SKIP_MISSED_SLOT,
                                missed.get(i).scheduledTime(),
                                "Collapsed older missed slot under FIRE_ONCE_IMMEDIATELY"
                        ));
                    }
                    triggers.add(new CatchUpTrigger(
                            jobId,
                            CatchUpTrigger.TriggerAction.EXECUTE_CATCH_UP,
                            latestMissed.scheduledTime(),
                            "Immediate single catch-up execution for most recent missed slot ("
                                    + missed.size() + " missed total)"
                    ));
                }
                case FIRE_ALL_MISSED -> {
                    for (SlotOccurrence m : missed) {
                        triggers.add(new CatchUpTrigger(
                                jobId,
                                CatchUpTrigger.TriggerAction.EXECUTE_CATCH_UP,
                                m.scheduledTime(),
                                "Sequential catch-up execution for missed slot under FIRE_ALL_MISSED"
                        ));
                    }
                }
                case SKIP_TO_NEXT -> {
                    for (SlotOccurrence m : missed) {
                        triggers.add(new CatchUpTrigger(
                                jobId,
                                CatchUpTrigger.TriggerAction.SKIP_MISSED_SLOT,
                                m.scheduledTime(),
                                "Skipped missed slot under SKIP_TO_NEXT; next scheduled at " + nextScheduled
                        ));
                    }
                }
                case ALERT_ONLY -> {
                    for (SlotOccurrence m : missed) {
                        triggers.add(new CatchUpTrigger(
                                jobId,
                                CatchUpTrigger.TriggerAction.EMIT_SLA_ALERT,
                                m.scheduledTime(),
                                "SLA missed execution alert (deadline was " + m.slaDeadline() + ")"
                        ));
                    }
                }
            }
        }

        if (policy.catchUpPolicy() == CatchUpPolicy.ALERT_ONLY) {
            for (SlotOccurrence l : late) {
                triggers.add(new CatchUpTrigger(
                        jobId,
                        CatchUpTrigger.TriggerAction.EMIT_SLA_ALERT,
                        l.scheduledTime(),
                        "SLA late execution breach alert (delay=" + l.delay() + ", grace=" + policy.slaGraceWindow() + ")"
                ));
            }
        }
        return triggers;
    }
}
