package com.msp.eventbus;

import java.util.function.Consumer;

/**
 * Diagnostic context provided when an exception occurs inside an event subscriber.
 */
public record SubscriberExceptionContext(
        EventBus eventBus,
        Object event,
        Class<?> eventType,
        Consumer<?> subscriber
) {
}
