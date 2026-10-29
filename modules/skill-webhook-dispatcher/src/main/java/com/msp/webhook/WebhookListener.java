package com.msp.webhook;

/**
 * Callback listener for observing webhook delivery lifecycle events.
 */
public interface WebhookListener {

    /**
     * Triggered when an event is successfully delivered (HTTP 2xx).
     */
    default void onSuccess(WebhookDelivery delivery) {
    }

    /**
     * Triggered when a single delivery attempt fails (non-2xx response or network error).
     */
    default void onFailure(WebhookDelivery delivery, Throwable cause, int attempt) {
    }

    /**
     * Triggered when all retry attempts have been exhausted and the event is routed to the Dead-Letter Queue (DLQ).
     */
    default void onDeadLetter(WebhookDelivery delivery, Throwable finalCause) {
    }
}
