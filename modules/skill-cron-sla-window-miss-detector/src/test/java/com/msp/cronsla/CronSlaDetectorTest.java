package com.msp.cronsla;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CronSlaDetectorTest {

    private static final Instant T0 = Instant.parse("2026-11-19T00:00:00Z");

    @Test
    void testOnTimeLateAndMissedDetectionWithFireOncePolicy() {
        CronSlaDetector detector = new CronSlaDetector();
        JobSlaPolicy policy = new JobSlaPolicy(
                "hourly-settlement",
                Duration.ofHours(1),
                Duration.ofMinutes(5),
                CatchUpPolicy.FIRE_ONCE_IMMEDIATELY
        );
        detector.registerPolicy(policy);

        // 4 hourly slots: 00:00, 01:00, 02:00, 03:00
        List<Instant> schedule = CronSlaDetector.generatePeriodicSchedule(
                T0, T0.plus(Duration.ofHours(3)), Duration.ofHours(1));
        assertEquals(4, schedule.size());

        // 00:00 -> executed at 00:02 (ON_TIME, within 5m grace)
        // 01:00 -> executed at 01:12 (LATE_SLA_BREACH, > 5m grace)
        // 02:00 -> MISSED
        // 03:00 -> MISSED
        List<Instant> actuals = List.of(
                T0.plus(Duration.ofMinutes(2)),
                T0.plus(Duration.ofHours(1)).plus(Duration.ofMinutes(12))
        );

        SlaEvaluationReport report = detector.evaluate(
                "hourly-settlement",
                schedule,
                actuals,
                T0.plus(Duration.ofHours(3)).plus(Duration.ofMinutes(30))
        );

        assertEquals(4, report.totalExpectedSlots());
        assertEquals(1, report.onTimeOccurrences().size());
        assertEquals(1, report.lateOccurrences().size());
        assertEquals(2, report.missedOccurrences().size());
        assertEquals(25.0, report.slaComplianceRatePercent());
        assertFalse(report.isFullyCompliant());

        // FIRE_ONCE_IMMEDIATELY collapses 02:00 into SKIP_MISSED_SLOT and executes 03:00
        assertEquals(2, report.catchUpTriggers().size());
        assertEquals(CatchUpTrigger.TriggerAction.SKIP_MISSED_SLOT, report.catchUpTriggers().get(0).action());
        assertEquals(T0.plus(Duration.ofHours(2)), report.catchUpTriggers().get(0).targetSlotTime());
        assertEquals(CatchUpTrigger.TriggerAction.EXECUTE_CATCH_UP, report.catchUpTriggers().get(1).action());
        assertEquals(T0.plus(Duration.ofHours(3)), report.catchUpTriggers().get(1).targetSlotTime());
    }

    @Test
    void testFireAllMissedPolicy() {
        CronSlaDetector detector = new CronSlaDetector();
        JobSlaPolicy policy = new JobSlaPolicy(
                "partition-compaction",
                Duration.ofMinutes(15),
                Duration.ofMinutes(2),
                CatchUpPolicy.FIRE_ALL_MISSED
        );
        detector.registerPolicy(policy);

        List<Instant> schedule = List.of(
                T0,
                T0.plus(Duration.ofMinutes(15)),
                T0.plus(Duration.ofMinutes(30))
        );

        SlaEvaluationReport report = detector.evaluate(
                "partition-compaction",
                schedule,
                List.of(), // 0 actual runs
                T0.plus(Duration.ofMinutes(45))
        );

        assertEquals(3, report.missedOccurrences().size());
        assertEquals(0.0, report.slaComplianceRatePercent());
        assertEquals(3, report.catchUpTriggers().size());
        assertTrue(report.catchUpTriggers().stream()
                .allMatch(t -> t.action() == CatchUpTrigger.TriggerAction.EXECUTE_CATCH_UP));
    }

    @Test
    void testSkipToNextAndAlertOnlyPolicies() {
        CronSlaDetector detector = new CronSlaDetector();
        JobSlaPolicy skipPolicy = new JobSlaPolicy(
                "metrics-ping",
                Duration.ofMinutes(10),
                Duration.ofMinutes(1),
                CatchUpPolicy.SKIP_TO_NEXT
        );
        detector.registerPolicy(skipPolicy);

        List<Instant> schedule = List.of(T0, T0.plus(Duration.ofMinutes(10)));
        SlaEvaluationReport skipReport = detector.evaluate(
                "metrics-ping", schedule, List.of(T0), T0.plus(Duration.ofMinutes(15)));

        assertEquals(1, skipReport.onTimeOccurrences().size());
        assertEquals(1, skipReport.missedOccurrences().size());
        assertEquals(50.0, skipReport.slaComplianceRatePercent());
        assertEquals(1, skipReport.catchUpTriggers().size());
        assertEquals(CatchUpTrigger.TriggerAction.SKIP_MISSED_SLOT, skipReport.catchUpTriggers().get(0).action());

        JobSlaPolicy alertPolicy = new JobSlaPolicy(
                "audit-export",
                Duration.ofMinutes(10),
                Duration.ofMinutes(1),
                CatchUpPolicy.ALERT_ONLY
        );
        detector.registerPolicy(alertPolicy);
        // Slot 1 is late (T0 + 3m), Slot 2 is missed
        SlaEvaluationReport alertReport = detector.evaluate(
                "audit-export",
                schedule,
                List.of(T0.plus(Duration.ofMinutes(3))),
                T0.plus(Duration.ofMinutes(15))
        );
        assertEquals(1, alertReport.lateOccurrences().size());
        assertEquals(1, alertReport.missedOccurrences().size());
        assertEquals(2, alertReport.catchUpTriggers().size());
        assertTrue(alertReport.catchUpTriggers().stream()
                .allMatch(t -> t.action() == CatchUpTrigger.TriggerAction.EMIT_SLA_ALERT));
    }

    @Test
    void testInGracePeriodSlotIsNotPrematurelyMarkedMissed() {
        CronSlaDetector detector = new CronSlaDetector();
        JobSlaPolicy policy = new JobSlaPolicy(
                "daily-backup",
                Duration.ofHours(24),
                Duration.ofMinutes(30),
                CatchUpPolicy.FIRE_ONCE_IMMEDIATELY
        );
        detector.registerPolicy(policy);

        // Evaluated at T0 + 10m, which is still inside the 30-minute SLA grace window of T0
        SlaEvaluationReport report = detector.evaluate(
                "daily-backup",
                List.of(T0),
                List.of(),
                T0.plus(Duration.ofMinutes(10))
        );

        assertEquals(0, report.missedOccurrences().size());
        assertEquals(100.0, report.slaComplianceRatePercent());
        assertTrue(report.isFullyCompliant());
    }
}
