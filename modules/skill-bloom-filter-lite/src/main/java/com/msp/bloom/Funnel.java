package com.msp.bloom;

/**
 * Funnel interface that transforms an object of type {@code T} into a stream of primitive bytes
 * fed into a {@link Sink}.
 *
 * @param <T> data type
 */
@FunctionalInterface
public interface Funnel<T> {

    /**
     * Sends a stream of data from the {@code from} object into the sink.
     *
     * @param from input element
     * @param into destination sink
     */
    void funnel(T from, Sink into);
}
