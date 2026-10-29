package com.msp.eventbus;

import java.time.Instant;
import java.util.Objects;

/**
 * Event wrapper produced when an event is published but has no matching subscribers.
 */
public record DeadLetterEvent(
        Object event,
        Instant timestamp
) {

    public DeadLetterEvent {
        Objects.requireNonNull(event, "event cannot be null");
        timestamp = timestamp != null ? timestamp : Instant.now();
    }

    public static DeadLetterEvent of(Object event) {
        return new DeadLetterEvent(event, Instant.now());
    }
}
