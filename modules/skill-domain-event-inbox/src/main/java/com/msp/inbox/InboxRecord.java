package com.msp.inbox;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable audit record representing an event stored in the inbox.
 */
public record InboxRecord(
        String eventId,
        String eventType,
        String payload,
        InboxStatus status,
        Instant receivedAt,
        Instant processedAt,
        String failureReason
) {
    public InboxRecord {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(receivedAt, "receivedAt must not be null");
    }

    public InboxRecord withStatus(InboxStatus newStatus, Instant processedAt, String failureReason) {
        return new InboxRecord(
                this.eventId,
                this.eventType,
                this.payload,
                newStatus,
                this.receivedAt,
                processedAt,
                failureReason
        );
    }
}
