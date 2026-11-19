package com.msp.sequencer;

/**
 * Callback interface to be implemented by consumer to process events published to the RingBuffer.
 *
 * @param <T> event type
 */
@FunctionalInterface
public interface EventHandler<T> {

    /**
     * Called when a publisher has committed an event to the RingBuffer.
     *
     * @param event      published event
     * @param sequence   sequence number of the event
     * @param endOfBatch flag indicating if this is the last event in a batch from the RingBuffer
     * @throws Exception if the event processing encounters an error
     */
    void onEvent(T event, long sequence, boolean endOfBatch) throws Exception;
}
