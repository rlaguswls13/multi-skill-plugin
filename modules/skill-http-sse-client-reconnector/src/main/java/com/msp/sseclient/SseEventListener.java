package com.msp.sseclient;

/**
 * Callback listener for Server-Sent Events lifecycle and messages.
 */
public interface SseEventListener {

    /**
     * Invoked when the SSE HTTP stream is successfully opened (HTTP 200).
     */
    default void onOpen() {}

    /**
     * Invoked when a complete SSE event frame is received and dispatched.
     *
     * @param event The parsed ServerSentEvent
     */
    void onEvent(ServerSentEvent event);

    /**
     * Invoked when an SSE comment frame (line starting with ':') is received.
     * Useful for keepalive ping monitoring.
     *
     * @param comment The comment line text without leading colon
     */
    default void onComment(String comment) {}

    /**
     * Invoked when an error occurs during connection, reading, or reconnecting.
     *
     * @param throwable The error or exception encountered
     */
    default void onError(Throwable throwable) {}

    /**
     * Invoked when the SSE client is closed and no further reconnection attempts will be made.
     */
    default void onClose() {}
}
