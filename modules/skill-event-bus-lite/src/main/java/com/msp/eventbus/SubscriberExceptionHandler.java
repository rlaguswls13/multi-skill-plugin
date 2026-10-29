package com.msp.eventbus;

/**
 * Strategy interface for handling exceptions thrown by event subscribers during event dispatching.
 */
@FunctionalInterface
public interface SubscriberExceptionHandler {

    /**
     * Handles an exception thrown by an event subscriber.
     *
     * @param exception the uncaught exception thrown by the listener
     * @param context   the dispatch context containing event and subscriber details
     */
    void handleException(Throwable exception, SubscriberExceptionContext context);
}
