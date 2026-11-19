package com.msp.sseclient;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Stateful parser for WHATWG Server-Sent Events (SSE) line streams.
 */
public class SseEventParser {

    private final SseEventListener listener;
    private final StringBuilder dataBuffer = new StringBuilder();
    private String currentEvent = null;
    private String currentId = null;
    private Long currentRetry = null;
    private String lastEventId = null;

    public SseEventParser(SseEventListener listener) {
        this.listener = Objects.requireNonNull(listener, "listener must not be null");
    }

    /**
     * Feed a single line into the SSE parser state machine.
     *
     * @param line Raw line received from SSE stream (trailing CRLF stripped)
     */
    public synchronized void processLine(String line) {
        if (line == null) {
            return;
        }

        // Empty line indicates end of an event frame -> dispatch
        if (line.isEmpty()) {
            dispatchEvent();
            return;
        }

        // Comment line
        if (line.startsWith(":")) {
            String comment = line.substring(1);
            if (comment.startsWith(" ")) {
                comment = comment.substring(1);
            }
            listener.onComment(comment);
            return;
        }

        // Field: Value parsing
        int colonIdx = line.indexOf(':');
        String field;
        String value;

        if (colonIdx >= 0) {
            field = line.substring(0, colonIdx);
            value = line.substring(colonIdx + 1);
            // Per spec: If value's first character is U+0020 SPACE, strip it
            if (value.startsWith(" ")) {
                value = value.substring(1);
            }
        } else {
            field = line;
            value = "";
        }

        switch (field) {
            case "event" -> this.currentEvent = value;
            case "data" -> {
                if (!dataBuffer.isEmpty()) {
                    dataBuffer.append("\n");
                }
                dataBuffer.append(value);
            }
            case "id" -> {
                // Spec: If value contains null char, ignore
                if (!value.contains("\u0000")) {
                    this.currentId = value;
                    this.lastEventId = value;
                }
            }
            case "retry" -> {
                try {
                    long retry = Long.parseLong(value.trim());
                    if (retry >= 0) {
                        this.currentRetry = retry;
                    }
                } catch (NumberFormatException ignored) {
                    // Ignore malformed retry field
                }
            }
            default -> {
                // Other fields are ignored per SSE specification
            }
        }
    }

    private void dispatchEvent() {
        // If data buffer is empty and no event or ID was specified, skip dispatch
        if (dataBuffer.isEmpty() && currentEvent == null && currentId == null && currentRetry == null) {
            return;
        }

        String data = dataBuffer.toString();
        ServerSentEvent event = new ServerSentEvent(currentId, currentEvent, data, currentRetry);

        // Reset per-event fields (lastEventId is preserved across events)
        dataBuffer.setLength(0);
        currentEvent = null;
        currentId = null;
        currentRetry = null;

        listener.onEvent(event);
    }

    /**
     * Resets the parser state (e.g. for a new connection).
     *
     * @param preserveLastEventId Whether to keep the lastEventId for the next connection
     */
    public synchronized void reset(boolean preserveLastEventId) {
        dataBuffer.setLength(0);
        currentEvent = null;
        currentId = null;
        currentRetry = null;
        if (!preserveLastEventId) {
            lastEventId = null;
        }
    }

    /**
     * Returns the latest tracked event ID.
     */
    public synchronized String getLastEventId() {
        return lastEventId;
    }

    /**
     * Manually overrides or initializes the last tracked event ID.
     */
    public synchronized void setLastEventId(String lastEventId) {
        this.lastEventId = lastEventId;
    }
}
