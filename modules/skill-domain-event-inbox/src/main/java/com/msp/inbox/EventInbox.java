package com.msp.inbox;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * In-memory Transactional Event Inbox for idempotent event consumption in distributed messaging systems.
 */
public class EventInbox {

    private final ConcurrentMap<String, InboxRecord> storage = new ConcurrentHashMap<>();
    private final Duration defaultPendingTimeout;

    public EventInbox(Duration defaultPendingTimeout) {
        this.defaultPendingTimeout = Objects.requireNonNull(defaultPendingTimeout, "defaultPendingTimeout must not be null");
    }

    public EventInbox() {
        this(Duration.ofMinutes(5));
    }

    /**
     * Attempts to acquire an event for processing.
     * Returns true if successfully acquired (new or retryable), false if already processed or actively being processed.
     *
     * @param eventId   unique event identifier
     * @param eventType type/topic of the event
     * @param payload   event content/payload
     * @return true if caller should process this event, false if duplicate/already processed
     */
    public boolean tryAcquire(String eventId, String eventType, String payload) {
        return tryAcquire(eventId, eventType, payload, defaultPendingTimeout);
    }

    /**
     * Attempts to acquire an event with a specific pending lock timeout.
     */
    public boolean tryAcquire(String eventId, String eventType, String payload, Duration pendingTimeout) {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Instant now = Instant.now();
        boolean[] acquiredFlag = new boolean[1];

        storage.compute(eventId, (id, existing) -> {
            if (existing == null) {
                // New event -> acquire
                acquiredFlag[0] = true;
                return new InboxRecord(id, eventType, payload, InboxStatus.PENDING, now, null, null);
            }

            if (existing.status() == InboxStatus.PROCESSED) {
                // Already completed -> reject
                return existing;
            }

            if (existing.status() == InboxStatus.PENDING) {
                // Check lock expiration
                if (Duration.between(existing.receivedAt(), now).compareTo(pendingTimeout) > 0) {
                    // Lock timed out -> re-acquire
                    acquiredFlag[0] = true;
                    return new InboxRecord(id, eventType, payload, InboxStatus.PENDING, now, null, null);
                }
                // Actively pending within timeout -> reject
                return existing;
            }

            // FAILED status -> allow retry
            acquiredFlag[0] = true;
            return new InboxRecord(id, eventType, payload, InboxStatus.PENDING, now, null, null);
        });

        return acquiredFlag[0];
    }

    /**
     * Marks the acquired event as successfully PROCESSED.
     */
    public void markProcessed(String eventId) {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Instant now = Instant.now();
        storage.computeIfPresent(eventId, (id, record) -> record.withStatus(InboxStatus.PROCESSED, now, null));
    }

    /**
     * Marks the event as FAILED with a reason.
     */
    public void markFailed(String eventId, String reason) {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Instant now = Instant.now();
        storage.computeIfPresent(eventId, (id, record) -> record.withStatus(InboxStatus.FAILED, now, reason));
    }

    /**
     * Checks if the event has been successfully processed.
     */
    public boolean isProcessed(String eventId) {
        InboxRecord record = storage.get(eventId);
        return record != null && record.status() == InboxStatus.PROCESSED;
    }

    /**
     * Retrieves the current audit record for an event.
     */
    public Optional<InboxRecord> getRecord(String eventId) {
        return Optional.ofNullable(storage.get(eventId));
    }

    /**
     * Purges records older than the given retention period.
     *
     * @return number of purged records
     */
    public int cleanupOldRecords(Duration retention) {
        Objects.requireNonNull(retention, "retention must not be null");
        Instant threshold = Instant.now().minus(retention);
        int purged = 0;

        for (var entry : storage.entrySet()) {
            InboxRecord r = entry.getValue();
            Instant referenceTime = r.processedAt() != null ? r.processedAt() : r.receivedAt();
            if (referenceTime.isBefore(threshold)) {
                if (storage.remove(entry.getKey(), r)) {
                    purged++;
                }
            }
        }
        return purged;
    }

    public int size() {
        return storage.size();
    }

    public void clear() {
        storage.clear();
    }
}
