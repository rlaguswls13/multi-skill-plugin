# `skill-cron-rate-smoothed-batch-scheduler`

Zero-dependency Java 21 **Rate-Smoothed Batch Scheduler** that distributes `N` batch items evenly across a target time window (`Instant start`, `Duration window`) into balanced execution slots.

## Highlights
- **Balanced Slot Sizing**: Avoids tail-heavy or front-loaded batches (e.g. 10 items with `maxBatchSize = 4` yields 3 slots of sizes `4, 3, 3`).
- **Deterministic Jitter**: Reproducible SplitMix64 jitter (`jitterRatio` in `[0.0, 0.5]`, `jitterSeed`) clamped strictly inside `[windowStart, windowEnd]`.
- **Anti-Hotspot Spread Strategies**: Supports `PRESERVE_ORDER`, `PRIORITY_DESC`, `ROUND_ROBIN_BY_KEY`, and `PRIORITY_THEN_KEY_SPREAD`.
- **Execution Timeline Plan**: Query `dueSlots(Instant)` and `slotsInRange(Instant, Instant)` on the immutable `BatchSchedulePlan`.

## Quick Start

```java
import com.msp.batchsched.*;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

public class Example {
    public static void main(String[] args) {
        List<BatchItem<String>> items = List.of(
                BatchItem.of("tenant-a", "job-1", 10),
                BatchItem.of("tenant-a", "job-2", 10),
                BatchItem.of("tenant-b", "job-3", 10),
                BatchItem.of("tenant-c", "job-4", 1)
        );

        BatchScheduleConfig config = BatchScheduleConfig.builder()
                .maxBatchSize(2)
                .maxConcurrency(2)
                .jitterRatio(0.15)
                .jitterSeed(42L)
                .spreadStrategy(SpreadStrategy.PRIORITY_THEN_KEY_SPREAD)
                .build();

        BatchSchedulePlan<String> plan = RateSmoothedBatchScheduler.plan(
                items, Instant.now(), Duration.ofMinutes(1), config
        );

        for (ScheduledSlot<String> slot : plan.slots()) {
            System.out.printf("Slot #%d @ %s (%d items)%n",
                    slot.slotIndex(), slot.scheduledAt(), slot.size());
        }
    }
}
```
