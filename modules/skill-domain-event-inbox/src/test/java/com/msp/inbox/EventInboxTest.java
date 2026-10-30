package com.msp.inbox;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class EventInboxTest {

    @Test
    @DisplayName("Acquire new event, process it, and reject subsequent duplicates")
    void testInboxLifecycleAndIdempotency() {
        EventInbox inbox = new EventInbox();
        String eventId = "evt-order-created-999";

        // 1. Initial acquire -> true
        assertTrue(inbox.tryAcquire(eventId, "OrderCreated", "{\"orderId\":999}"));
        assertFalse(inbox.isProcessed(eventId));

        // 2. Concurrent acquire while pending -> false
        assertFalse(inbox.tryAcquire(eventId, "OrderCreated", "{\"orderId\":999}"));

        // 3. Mark processed
        inbox.markProcessed(eventId);
        assertTrue(inbox.isProcessed(eventId));

        // 4. Duplicate event arrives after processed -> false (Idempotent!)
        assertFalse(inbox.tryAcquire(eventId, "OrderCreated", "{\"orderId\":999}"));
    }

    @Test
    @DisplayName("Failed event can be retried and re-acquired")
    void testFailedEventRetry() {
        EventInbox inbox = new EventInbox();
        String eventId = "evt-payment-charge-101";

        assertTrue(inbox.tryAcquire(eventId, "PaymentCharge", "{\"amount\":5000}"));
        inbox.markFailed(eventId, "Network timeout to PG");

        // Retry acquisition should succeed
        assertTrue(inbox.tryAcquire(eventId, "PaymentCharge", "{\"amount\":5000}"));
        inbox.markProcessed(eventId);
        assertTrue(inbox.isProcessed(eventId));
    }

    @Test
    @DisplayName("Pending timeout allows re-acquisition of stalled event")
    void testPendingTimeoutReacquisition() throws InterruptedException {
        EventInbox inbox = new EventInbox(Duration.ofMillis(50));
        String eventId = "evt-stalled-node";

        assertTrue(inbox.tryAcquire(eventId, "SyncUser", "data"));
        assertFalse(inbox.tryAcquire(eventId, "SyncUser", "data"));

        // Sleep past pending timeout
        Thread.sleep(80);

        // Should allow reacquisition
        assertTrue(inbox.tryAcquire(eventId, "SyncUser", "data"));
    }
}
