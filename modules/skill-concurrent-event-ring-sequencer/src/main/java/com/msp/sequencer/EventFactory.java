package com.msp.sequencer;

/**
 * Factory for creating pre-allocated event instances in the RingBuffer.
 *
 * @param <T> event type
 */
@FunctionalInterface
public interface EventFactory<T> {
    T newInstance();
}
