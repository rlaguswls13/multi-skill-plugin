package com.msp.dlq;

/**
 * Status of a dead letter message in the quarantine queue.
 */
public enum MessageStatus {
    QUARANTINED,
    REPLAYED,
    DISCARDED
}
