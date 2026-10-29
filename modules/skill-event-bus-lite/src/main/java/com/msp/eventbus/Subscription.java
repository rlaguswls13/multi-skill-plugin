package com.msp.eventbus;

/**
 * Handle representing an active event subscription.
 * Implements {@link AutoCloseable} so subscriptions can be automatically cancelled.
 */
public interface Subscription extends AutoCloseable {

    /**
     * The type of event subscribed to.
     */
    Class<?> eventType();

    /**
     * Whether this subscription is currently active.
     */
    boolean isSubscribed();

    /**
     * Cancels this subscription so no further events are received.
     */
    void unsubscribe();

    @Override
    default void close() {
        unsubscribe();
    }
}
