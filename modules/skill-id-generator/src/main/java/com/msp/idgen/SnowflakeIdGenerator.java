package com.msp.idgen;

import java.time.Instant;
import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * Thread-safe 64-bit Snowflake ID generator.
 *
 * <p>Bit distribution (64-bit signed positive integer):
 * <ul>
 *   <li>1 bit: Unused sign bit (always 0)</li>
 *   <li>41 bits: Millisecond timestamp delta from epoch (~69.7 years capacity)</li>
 *   <li>10 bits: Machine / Node ID (0 - 1023)</li>
 *   <li>12 bits: Per-millisecond sequence counter (0 - 4095)</li>
 * </ul>
 *
 * <p>Includes built-in defense against system clock backwards drift.
 */
public final class SnowflakeIdGenerator {

    /** Default custom epoch: 2024-01-01 00:00:00 UTC (1,704,067,200,000 ms). */
    public static final long DEFAULT_EPOCH = 1704067200000L;

    public static final long NODE_ID_BITS = 10L;
    public static final long SEQUENCE_BITS = 12L;

    public static final long MAX_NODE_ID = (1L << NODE_ID_BITS) - 1L; // 1023
    public static final long MAX_SEQUENCE = (1L << SEQUENCE_BITS) - 1L; // 4095

    private static final long TIMESTAMP_SHIFT = NODE_ID_BITS + SEQUENCE_BITS; // 22
    private static final long NODE_ID_SHIFT = SEQUENCE_BITS; // 12
    private static final long DEFAULT_MAX_BACKWARD_DRIFT_MS = 5L;

    private final long nodeId;
    private final long epoch;
    private final long maxBackwardDriftMs;
    private final LongSupplier timeSource;

    private long lastTimestamp = -1L;
    private long sequence = 0L;

    /**
     * Creates a SnowflakeIdGenerator with the default epoch (2024-01-01 UTC).
     *
     * @param nodeId worker/node ID (0 - 1023)
     */
    public SnowflakeIdGenerator(long nodeId) {
        this(nodeId, DEFAULT_EPOCH, DEFAULT_MAX_BACKWARD_DRIFT_MS, System::currentTimeMillis);
    }

    /**
     * Creates a SnowflakeIdGenerator with a custom epoch.
     *
     * @param nodeId worker/node ID (0 - 1023)
     * @param epoch  epoch timestamp in milliseconds
     */
    public SnowflakeIdGenerator(long nodeId, long epoch) {
        this(nodeId, epoch, DEFAULT_MAX_BACKWARD_DRIFT_MS, System::currentTimeMillis);
    }

    /**
     * Static factory method to create a SnowflakeIdGenerator from datacenter ID and worker ID.
     *
     * @param datacenterId datacenter ID (0 - 31)
     * @param workerId     worker ID within datacenter (0 - 31)
     * @return SnowflakeIdGenerator instance
     */
    public static SnowflakeIdGenerator of(long datacenterId, long workerId) {
        if (datacenterId < 0 || datacenterId > 31) {
            throw new IllegalArgumentException("datacenterId must be between 0 and 31: " + datacenterId);
        }
        if (workerId < 0 || workerId > 31) {
            throw new IllegalArgumentException("workerId must be between 0 and 31: " + workerId);
        }
        return new SnowflakeIdGenerator((datacenterId << 5) | workerId);
    }

    /**
     * Full constructor with custom settings and injectable time source.
     */
    public SnowflakeIdGenerator(long nodeId, long epoch, long maxBackwardDriftMs, LongSupplier timeSource) {
        if (nodeId < 0 || nodeId > MAX_NODE_ID) {
            throw new IllegalArgumentException("nodeId must be between 0 and " + MAX_NODE_ID + ": " + nodeId);
        }
        if (epoch < 0) {
            throw new IllegalArgumentException("epoch cannot be negative: " + epoch);
        }
        if (maxBackwardDriftMs < 0) {
            throw new IllegalArgumentException("maxBackwardDriftMs cannot be negative: " + maxBackwardDriftMs);
        }
        this.nodeId = nodeId;
        this.epoch = epoch;
        this.maxBackwardDriftMs = maxBackwardDriftMs;
        this.timeSource = Objects.requireNonNull(timeSource, "timeSource must not be null");
    }

    /**
     * Generates the next unique 64-bit Snowflake ID.
     *
     * @return 64-bit positive long ID
     */
    public synchronized long nextId() {
        long currentTimestamp = timeSource.getAsLong();

        if (currentTimestamp < lastTimestamp) {
            long drift = lastTimestamp - currentTimestamp;
            if (drift <= maxBackwardDriftMs) {
                // Wait for the clock to catch up
                currentTimestamp = waitUntilNextTime(lastTimestamp);
            } else {
                throw new ClockMovedBackwardsException(currentTimestamp, lastTimestamp);
            }
        }

        if (currentTimestamp == lastTimestamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0) {
                // Sequence exhausted for this millisecond, wait until next millisecond
                currentTimestamp = waitUntilNextTime(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }

        lastTimestamp = currentTimestamp;

        return ((currentTimestamp - epoch) << TIMESTAMP_SHIFT)
                | (nodeId << NODE_ID_SHIFT)
                | sequence;
    }

    /**
     * Generates the next ID as a String.
     */
    public String nextIdStr() {
        return Long.toString(nextId());
    }

    private long waitUntilNextTime(long lastTs) {
        long current = timeSource.getAsLong();
        while (current <= lastTs) {
            Thread.onSpinWait();
            current = timeSource.getAsLong();
        }
        return current;
    }

    /**
     * Extracts the original UTC millisecond timestamp from an ID.
     */
    public long extractTimestamp(long id) {
        return (id >>> TIMESTAMP_SHIFT) + epoch;
    }

    /**
     * Extracts the Instant from an ID.
     */
    public Instant extractInstant(long id) {
        return Instant.ofEpochMilli(extractTimestamp(id));
    }

    /**
     * Extracts the node ID from an ID.
     */
    public long extractNodeId(long id) {
        return (id >>> NODE_ID_SHIFT) & MAX_NODE_ID;
    }

    /**
     * Extracts the sequence number from an ID.
     */
    public long extractSequence(long id) {
        return id & MAX_SEQUENCE;
    }

    public long getNodeId() {
        return nodeId;
    }

    public long getEpoch() {
        return epoch;
    }
}
