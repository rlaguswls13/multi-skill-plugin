package com.msp.cronmerge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CronIntervalMerger & TimeInterval Tests")
class CronIntervalMergerTest {

    @Test
    @DisplayName("TimeInterval validation and basic properties")
    void testTimeIntervalBasics() {
        Instant t1 = Instant.parse("2026-10-31T10:00:00Z");
        Instant t2 = Instant.parse("2026-10-31T10:30:00Z");

        TimeInterval interval = new TimeInterval(t1, t2);
        assertEquals(Duration.ofMinutes(30), interval.duration());

        // Invalid: start after end
        assertThrows(IllegalArgumentException.class, () -> new TimeInterval(t2, t1));
    }

    @Test
    @DisplayName("TimeInterval overlaps, contiguous, and merge operations")
    void testTimeIntervalOperations() {
        Instant t0 = Instant.parse("2026-10-31T09:00:00Z");
        Instant t1 = Instant.parse("2026-10-31T10:00:00Z");
        Instant t2 = Instant.parse("2026-10-31T10:30:00Z");
        Instant t3 = Instant.parse("2026-10-31T11:00:00Z");

        TimeInterval i1 = new TimeInterval(t1, t2); // [10:00, 10:30]
        TimeInterval iContiguous = new TimeInterval(t2, t3); // [10:30, 11:00]
        TimeInterval iDisjoint = new TimeInterval(t0, t1.minusSeconds(60)); // [09:00, 09:59]

        // Contiguous
        assertTrue(i1.isContiguousWith(iContiguous));
        assertTrue(i1.overlaps(iContiguous)); // At endpoint t2
        TimeInterval merged = i1.merge(iContiguous);
        assertEquals(new TimeInterval(t1, t3), merged);

        // Disjoint
        assertFalse(i1.overlaps(iDisjoint));
        assertFalse(i1.isContiguousWith(iDisjoint));
        assertThrows(IllegalArgumentException.class, () -> i1.merge(iDisjoint));
    }

    @Test
    @DisplayName("mergeIntervals: overlapping, contiguous, fully-enclosed, and disjoint intervals")
    void testMergeIntervals() {
        Instant t10_00 = Instant.parse("2026-10-31T10:00:00Z");
        Instant t10_15 = Instant.parse("2026-10-31T10:15:00Z");
        Instant t10_30 = Instant.parse("2026-10-31T10:30:00Z");
        Instant t10_45 = Instant.parse("2026-10-31T10:45:00Z");
        Instant t11_00 = Instant.parse("2026-10-31T11:00:00Z");
        Instant t11_15 = Instant.parse("2026-10-31T11:15:00Z");
        Instant t11_30 = Instant.parse("2026-10-31T11:30:00Z");

        // Intervals:
        // [10:00, 10:30]
        // [10:15, 10:45] (overlaps first)
        // [10:45, 11:00] (contiguous with second)
        // [11:15, 11:30] (disjoint)
        List<TimeInterval> input = List.of(
                new TimeInterval(t10_45, t11_00),
                new TimeInterval(t10_00, t10_30),
                new TimeInterval(t11_15, t11_30),
                new TimeInterval(t10_15, t10_45)
        );

        List<TimeInterval> result = CronIntervalMerger.mergeIntervals(input);
        assertEquals(2, result.size());
        assertEquals(new TimeInterval(t10_00, t11_00), result.get(0));
        assertEquals(new TimeInterval(t11_15, t11_30), result.get(1));
    }

    @Test
    @DisplayName("mergeIntervals: empty and single interval lists")
    void testMergeIntervalsBoundary() {
        assertTrue(CronIntervalMerger.mergeIntervals(null).isEmpty());
        assertTrue(CronIntervalMerger.mergeIntervals(List.of()).isEmpty());

        TimeInterval single = new TimeInterval(
                Instant.parse("2026-10-31T10:00:00Z"),
                Instant.parse("2026-10-31T10:30:00Z")
        );
        List<TimeInterval> singleResult = CronIntervalMerger.mergeIntervals(List.of(single));
        assertEquals(1, singleResult.size());
        assertEquals(single, singleResult.get(0));
    }

    @Test
    @DisplayName("calculateMergedExecutionWindows: expanding single job overlapping itself")
    void testCalculateMergedWindowsSelfOverlapping() {
        // Job running every 10 minutes (* / 10), duration 15 minutes
        ScheduledJob job = new ScheduledJob("BatchJob", "*/10 * * * *", Duration.ofMinutes(15));

        Instant windowStart = Instant.parse("2026-10-31T10:00:00Z");
        Instant windowEnd = Instant.parse("2026-10-31T10:35:00Z");

        // Triggers at 10:00 (end 10:15), 10:10 (end 10:25), 10:20 (end 10:35), 10:30 (end 10:45)
        // All intervals overlap into one single continuous block: [10:00, 10:45]
        List<TimeInterval> merged = CronIntervalMerger.calculateMergedExecutionWindows(
                List.of(job), windowStart, windowEnd
        );

        assertEquals(1, merged.size());
        assertEquals(new TimeInterval(windowStart, Instant.parse("2026-10-31T10:45:00Z")), merged.get(0));
    }

    @Test
    @DisplayName("calculateMergedExecutionWindows: multiple distinct jobs merging windows")
    void testCalculateMergedWindowsMultipleJobs() {
        // Job A at 10:00 for 25 min -> [10:00, 10:25]
        ScheduledJob jobA = new ScheduledJob("JobA", "0 10 * * *", Duration.ofMinutes(25));
        // Job B at 10:20 for 20 min -> [10:20, 10:40]
        ScheduledJob jobB = new ScheduledJob("JobB", "20 10 * * *", Duration.ofMinutes(20));
        // Job C at 11:00 for 15 min -> [11:00, 11:15]
        ScheduledJob jobC = new ScheduledJob("JobC", "0 11 * * *", Duration.ofMinutes(15));

        Instant windowStart = Instant.parse("2026-10-31T09:00:00Z");
        Instant windowEnd = Instant.parse("2026-10-31T12:00:00Z");

        List<TimeInterval> merged = CronIntervalMerger.calculateMergedExecutionWindows(
                List.of(jobA, jobB, jobC), windowStart, windowEnd
        );

        assertEquals(2, merged.size());
        assertEquals(new TimeInterval(Instant.parse("2026-10-31T10:00:00Z"), Instant.parse("2026-10-31T10:40:00Z")), merged.get(0));
        assertEquals(new TimeInterval(Instant.parse("2026-10-31T11:00:00Z"), Instant.parse("2026-10-31T11:15:00Z")), merged.get(1));
    }
}
