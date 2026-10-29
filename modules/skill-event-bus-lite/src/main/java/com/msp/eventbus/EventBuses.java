package com.msp.eventbus;

import java.util.concurrent.Executor;

/**
 * Static factory facade for creating and accessing EventBus instances.
 */
public final class EventBuses {

    private static final EventBus DEFAULT_BUS = EventBus.create();

    private EventBuses() {
    }

    public static EventBus defaultBus() {
        return DEFAULT_BUS;
    }

    public static EventBus create() {
        return EventBus.create();
    }

    public static EventBus createAsync(Executor executor) {
        return EventBus.builder().executor(executor).build();
    }

    public static EventBus.Builder builder() {
        return EventBus.builder();
    }
}
