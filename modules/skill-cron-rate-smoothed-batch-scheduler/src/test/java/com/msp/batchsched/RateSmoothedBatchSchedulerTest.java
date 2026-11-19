package com.msp.batchsched;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RateSmoothedBatchSchedulerTest {

    private static final Instant START = Instant.parse("2026-11-19T10:00:00Z");

    @Test
    void distributesItemsUniformlyWithBalancedSlotSizes() {
        List<String> payloads = List.of("a", "b", "c", "d", "e", "f", "g", "h", "i", "j");
        BatchScheduleConfig config = BatchScheduleConfig.builder()
                .maxBatchSize(4)
                .maxConcurrency(2)
                .build();

        BatchSchedulePlan<String> plan = RateSmoothedBatchScheduler.planRaw(payloads, START, Duration.ofSeconds(60), config);

        assertEquals(10, plan.totalItems());
        assertEquals(3, plan.slotCount());
        assertEquals(Duration.ofSeconds(20), plan.nominalInterval());
        assertEquals(4, plan.maxSlotSize());

        // Balanced sizes: 4, 3, 3
        assertEquals(4, plan.slots().get(0).size());
        assertEquals(3, plan.slots().get(1).size());
        assertEquals(3, plan.slots().get(2).size());

        assertEquals(START, plan.slots().get(0).scheduledAt());
        assertEquals(START.plusSeconds(20), plan.slots().get(1).scheduledAt());
        assertEquals(START.plusSeconds(40), plan.slots().get(2).scheduledAt());
        assertEquals(2, plan.slots().get(0).maxConcurrency());
    }

    @Test
    void deterministicJitterIsReproducibleAndClampedWithinWindow() {
        List<BatchItem<Integer>> items = List.of(
                BatchItem.of("k", 1), BatchItem.of("k", 2),
                BatchItem.of("k", 3), BatchItem.of("k", 4),
                BatchItem.of("k", 5)
        );
        BatchScheduleConfig config = BatchScheduleConfig.builder()
                .maxBatchSize(1)
                .jitterRatio(0.25)
                .jitterSeed(99L)
                .build();

        BatchSchedulePlan<Integer> run1 = RateSmoothedBatchScheduler.plan(items, START, Duration.ofSeconds(100), config);
        BatchSchedulePlan<Integer> run2 = RateSmoothedBatchScheduler.plan(items, START, Duration.ofSeconds(100), config);

        assertEquals(run1, run2);

        Instant end = START.plusSeconds(100);
        boolean anyJitterApplied = false;
        for (ScheduledSlot<Integer> slot : run1.slots()) {
            assertFalse(slot.scheduledAt().isBefore(START));
            assertFalse(slot.scheduledAt().isAfter(end));
            assertTrue(Math.abs(slot.jitterOffset().toMillis()) <= 5_000); // 25% of 20s = 5s
            if (!slot.jitterOffset().isZero()) {
                anyJitterApplied = true;
            }
        }
        assertTrue(anyJitterApplied, "Expected non-zero jitter on at least one slot");
    }

    @Test
    void roundRobinByKeyPreventsTenantHotspotting() {
        List<BatchItem<String>> items = List.of(
                BatchItem.of("tenant-A", "a1"),
                BatchItem.of("tenant-A", "a2"),
                BatchItem.of("tenant-A", "a3"),
                BatchItem.of("tenant-B", "b1"),
                BatchItem.of("tenant-B", "b2"),
                BatchItem.of("tenant-C", "c1")
        );
        BatchScheduleConfig config = BatchScheduleConfig.builder()
                .maxBatchSize(1)
                .spreadStrategy(SpreadStrategy.ROUND_ROBIN_BY_KEY)
                .build();

        BatchSchedulePlan<String> plan = RateSmoothedBatchScheduler.plan(items, START, Duration.ofSeconds(60), config);
        List<String> keys = plan.slots().stream().map(s -> s.items().get(0).key()).toList();
        assertEquals(List.of("tenant-A", "tenant-B", "tenant-C", "tenant-A", "tenant-B", "tenant-A"), keys);
    }

    @Test
    void priorityThenKeySpreadSchedulesHighPriorityFirstAndInterleavesWithinTier() {
        List<BatchItem<String>> items = List.of(
                BatchItem.of("t1", "low-1", 1),
                BatchItem.of("t1", "high-1", 10),
                BatchItem.of("t1", "high-2", 10),
                BatchItem.of("t2", "high-3", 10),
                BatchItem.of("t2", "low-2", 1)
        );
        BatchScheduleConfig config = BatchScheduleConfig.builder()
                .maxBatchSize(1)
                .spreadStrategy(SpreadStrategy.PRIORITY_THEN_KEY_SPREAD)
                .build();

        BatchSchedulePlan<String> plan = RateSmoothedBatchScheduler.plan(items, START, Duration.ofSeconds(50), config);
        List<String> payloads = plan.slots().stream().map(s -> s.items().get(0).payload()).toList();
        assertEquals(List.of("high-1", "high-3", "high-2", "low-1", "low-2"), payloads);
    }

    @Test
    void dueSlotsAndRangeQueriesAndEmptyWindowEdgeCases() {
        BatchSchedulePlan<String> empty = RateSmoothedBatchScheduler.plan(List.of(), START, Duration.ofMinutes(5));
        assertTrue(empty.isEmpty());
        assertEquals(0, empty.slotCount());
        assertEquals(0, empty.maxSlotSize());

        BatchSchedulePlan<String> single = RateSmoothedBatchScheduler.plan(
                List.of(BatchItem.of("k", "only")), START, Duration.ofSeconds(30));
        assertEquals(1, single.slotCount());
        assertEquals(START, single.slots().get(0).scheduledAt());
        assertEquals(1, single.dueSlots(START).size());
        assertEquals(0, single.dueSlots(START.minusSeconds(1)).size());
        assertEquals(1, single.slotsInRange(START, START.plusSeconds(1)).size());
    }

    @Test
    void invalidConfigsThrowIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> BatchScheduleConfig.builder().maxBatchSize(0).build());
        assertThrows(IllegalArgumentException.class, () -> BatchScheduleConfig.builder().maxConcurrency(0).build());
        assertThrows(IllegalArgumentException.class, () -> BatchScheduleConfig.builder().jitterRatio(0.6).build());
        assertThrows(IllegalArgumentException.class, () -> BatchScheduleConfig.builder().jitterRatio(-0.1).build());
        assertThrows(IllegalArgumentException.class, () ->
                RateSmoothedBatchScheduler.plan(List.of(), START, Duration.ofSeconds(-1)));
    }
}
