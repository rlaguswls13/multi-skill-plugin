package com.msp.sequencer;

/**
 * Translator for populating an existing event with data.
 *
 * @param <T> event type
 */
@FunctionalInterface
public interface EventTranslator<T> {

    /**
     * Translate a data representation into the pre-allocated event.
     *
     * @param event    the target event
     * @param sequence sequence number of the event
     */
    void translateTo(T event, long sequence);
}
