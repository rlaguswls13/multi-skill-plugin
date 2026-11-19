package com.msp.sseclient;

import java.util.Objects;

/**
 * Immutable representation of a Server-Sent Event (SSE) frame per WHATWG specification.
 *
 * @param id          Event identifier (corresponds to {@code id:} field), or null if not provided
 * @param event       Event type (corresponds to {@code event:} field), defaults to "message" if null
 * @param data        Event payload data (corresponds to {@code data:} field)
 * @param retryMillis Reconnection time directive in milliseconds (corresponds to {@code retry:} field), or null
 */
public record ServerSentEvent(
        String id,
        String event,
        String data,
        Long retryMillis
) {
    public static final String DEFAULT_EVENT_TYPE = "message";

    public ServerSentEvent {
        if (event == null || event.isBlank()) {
            event = DEFAULT_EVENT_TYPE;
        }
        if (data == null) {
            data = "";
        }
    }

    /**
     * Checks if an ID is present on this event.
     */
    public boolean hasId() {
        return id != null && !id.isBlank();
    }

    /**
     * Checks if this event contains payload data.
     */
    public boolean hasData() {
        return data != null && !data.isEmpty();
    }

    /**
     * Checks if a custom reconnection retry directive was provided.
     */
    public boolean hasRetry() {
        return retryMillis != null && retryMillis > 0;
    }
}
