package com.msp.cronhist;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class CronExecutionTrackerTest {

    private CronExecutionTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new CronExecutionTracker(50);
    }

    @Test
    @DisplayName("Calculates accurate drift, duration, and rolling statistics")
    void testExecutionStatistics() {
        Instant base = Instant.parse("2026-11-01T12:00:00Z");

        // 3 executions with known drift and duration
        // Ex 1: scheduled 12:00:00, started 12:00:01 (drift 1000ms), finished 12:00:03 (duration 2000ms), success
        tracker.recordExecution("task1", base, base.plusMillis(1000), base.plusMillis(3000), true);

        // Ex 2: scheduled 12:01:00, started 12:01:02 (drift 2000ms), finished 12:01:06 (duration 4000ms), success
        Instant s2 = base.plus(Duration.ofMinutes(1));
        tracker.recordExecution("task1", s2, s2.plusMillis(2000), s2.plusMillis(6000), true);

        // Ex 3: scheduled 12:02:00, started 12:02:00 (drift 0ms), finished 12:02:03 (duration 3000ms), failure
        Instant s3 = base.plus(Duration.ofMinutes(2));
        tracker.recordExecution("task1", s3, s3, s3.plusMillis(3000), false);

        Optional<ExecutionStatistics> statsOpt = tracker.getStatistics("task1");
        assertTrue(statsOpt.isPresent());
        ExecutionStatistics stats = statsOpt.get();

        assertEquals(3, stats.sampleCount());
        assertEquals(2, stats.successCount());
        assertEquals(1, stats.failureCount());
        assertEquals(2.0 / 3.0, stats.successRate(), 0.001);

        // Mean drift = (1000 + 2000 + 0) / 3 = 1000 ms
        assertEquals(Duration.ofMillis(1000), stats.meanDrift());
        assertEquals(Duration.ofMillis(0), stats.minDrift());
        assertEquals(Duration.ofMillis(2000), stats.maxDrift());

        // Mean duration = (2000 + 4000 + 3000) / 3 = 3000 ms
        assertEquals(Duration.ofMillis(3000), stats.meanDuration());
        assertEquals(Duration.ofMillis(2000), stats.minDuration());
        assertEquals(Duration.ofMillis(4000), stats.maxDuration());
        assertTrue(stats.durationStdDevMs() > 0.0);
    }

    @Test
    @DisplayName("Detects severe schedule drift when start time lags beyond threshold")
    void testSevereDriftAnomaly() {
        tracker.setTaskPolicy("slow-worker", AnomalyPolicy.builder()
                .maxAllowedDrift(Duration.ofSeconds(2))
                .build());

        List<AnomalyEvent> captured = new ArrayList<>();
        tracker.addAnomalyListener(captured::add);

        Instant scheduled = Instant.parse("2026-11-01T12:00:00Z");
        // Actual start is 5 seconds late (exceeds 2s limit)
        Instant actualStart = scheduled.plusSeconds(5);
        Instant finish = actualStart.plusSeconds(1);

        tracker.recordExecution("slow-worker", scheduled, actualStart, finish, true);

        assertEquals(1, captured.size());
        assertEquals(AnomalyType.SEVERE_DRIFT, captured.get(0).type());
        assertTrue(captured.get(0).message().contains("5000 ms"));
    }

    @Test
    @DisplayName("Detects execution overrun when job duration exceeds recurrence interval")
    void testExecutionOverrunAnomaly() {
        tracker.setTaskPolicy("heavy-batch", AnomalyPolicy.builder()
                .expectedInterval(Duration.ofSeconds(10))
                .build());

        List<AnomalyEvent> captured = new ArrayList<>();
        tracker.addAnomalyListener(captured::add);

        Instant scheduled = Instant.parse("2026-11-01T12:00:00Z");
        Instant actualStart = scheduled.plusMillis(100);
        // Execution took 15 seconds, exceeding 10s interval
        Instant finish = actualStart.plusSeconds(15);

        tracker.recordExecution("heavy-batch", scheduled, actualStart, finish, true);

        assertEquals(1, captured.size());
        assertEquals(AnomalyType.EXECUTION_OVERRUN, captured.get(0).type());
        assertTrue(captured.get(0).message().contains("risking overlapping"));
    }

    @Test
    @DisplayName("Detects failure spike when consecutive failures exceed threshold")
    void testFailureSpikeAnomaly() {
        tracker.setTaskPolicy("unstable-task", AnomalyPolicy.builder()
                .consecutiveFailureThreshold(2)
                .build());

        List<AnomalyEvent> captured = new ArrayList<>();
        tracker.addAnomalyListener(captured::add);

        Instant base = Instant.parse("2026-11-01T12:00:00Z");

        // Fail 1: below threshold
        tracker.recordExecution("unstable-task", base, base, base.plusSeconds(1), false);
        assertTrue(captured.isEmpty());

        // Fail 2: reaches threshold 2 -> triggers FAILURE_SPIKE
        Instant t2 = base.plusSeconds(60);
        tracker.recordExecution("unstable-task", t2, t2, t2.plusSeconds(1), false);

        assertEquals(1, captured.size());
        assertEquals(AnomalyType.FAILURE_SPIKE, captured.get(0).type());

        // Succeed: resets consecutive count
        Instant t3 = base.plusSeconds(120);
        tracker.recordExecution("unstable-task", t3, t3, t3.plusSeconds(1), true);

        // Fail 1 after success: should not trigger
        Instant t4 = base.plusSeconds(180);
        tracker.recordExecution("unstable-task", t4, t4, t4.plusSeconds(1), false);
        assertEquals(1, captured.size());
    }

    @Test
    @DisplayName("Evicts oldest executions when capacity limit is reached")
    void testHistoryCapacityRetention() {
        CronExecutionTracker boundedTracker = new CronExecutionTracker(3);
        Instant base = Instant.parse("2026-11-01T12:00:00Z");

        for (int i = 0; i < 5; i++) {
            Instant t = base.plusSeconds(i * 10);
            boundedTracker.recordExecution("job1", t, t, t.plusSeconds(1), true);
        }

        List<ExecutionRecord> history = boundedTracker.getHistory("job1", 10);
        assertEquals(3, history.size(), "Only last 3 executions should be retained");
        assertEquals(base.plusSeconds(20), history.get(0).scheduledTime());
        assertEquals(base.plusSeconds(40), history.get(2).scheduledTime());
    }
}
