package com.msp.idgen;

/**
 * Thrown when the system clock moves backwards beyond the configured threshold,
 * which could lead to ID collisions.
 */
public class ClockMovedBackwardsException extends RuntimeException {

    private final long currentTimestamp;
    private final long lastTimestamp;

    public ClockMovedBackwardsException(long currentTimestamp, long lastTimestamp) {
        super(String.format("Clock moved backwards! Refusing to generate ID for %d milliseconds (current: %d, last: %d)",
                lastTimestamp - currentTimestamp, currentTimestamp, lastTimestamp));
        this.currentTimestamp = currentTimestamp;
        this.lastTimestamp = lastTimestamp;
    }

    public long getCurrentTimestamp() {
        return currentTimestamp;
    }

    public long getLastTimestamp() {
        return lastTimestamp;
    }
}
