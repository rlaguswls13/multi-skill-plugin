package com.msp.poolwatch;

import java.time.Instant;

/**
 * Point-in-time metrics snapshot of a {@link java.util.concurrent.ThreadPoolExecutor}.
 *
 * @param poolName                 identifier of the monitored pool
 * @param corePoolSize             configured core pool size
 * @param maximumPoolSize          configured maximum pool size
 * @param poolSize                 current total number of threads in the pool
 * @param activeCount              approximate number of threads actively executing tasks
 * @param largestPoolSize          peak number of threads that have ever simultaneously existed
 * @param taskCount                approximate total number of tasks scheduled for execution
 * @param completedTaskCount       approximate total number of tasks that completed execution
 * @param queueSize                current number of tasks awaiting execution in queue
 * @param queueRemainingCapacity   remaining queue capacity
 * @param queueSaturationRatio     ratio of queue occupancy (0.0 to 1.0)
 * @param activeThreadRatio        ratio of active workers relative to maximum pool size (0.0 to 1.0)
 * @param rejectedCount            cumulative number of rejected tasks
 * @param timestamp                snapshot timestamp
 */
public record PoolSnapshot(
        String poolName,
        int corePoolSize,
        int maximumPoolSize,
        int poolSize,
        int activeCount,
        int largestPoolSize,
        long taskCount,
        long completedTaskCount,
        int queueSize,
        int queueRemainingCapacity,
        double queueSaturationRatio,
        double activeThreadRatio,
        long rejectedCount,
        Instant timestamp
) {
    public boolean isQueueSaturated(double threshold) {
        return queueSaturationRatio >= threshold;
    }

    public boolean isActiveWorkerSaturated(double threshold) {
        return activeThreadRatio >= threshold;
    }
}
