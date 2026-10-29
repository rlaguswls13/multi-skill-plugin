package com.msp.eventbus;

/**
 * Handler invoked when an event is published but has no registered subscribers.
 */
@FunctionalInterface
public interface DeadLetterHandler {

    /**
     * Handles an unhandled dead-letter event.
     */
    void handleDeadLetter(DeadLetterEvent deadLetter);
}
