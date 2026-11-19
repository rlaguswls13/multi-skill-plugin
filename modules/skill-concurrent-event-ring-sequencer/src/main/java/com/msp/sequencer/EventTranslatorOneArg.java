package com.msp.sequencer;

/**
 * Translator for populating an existing event with single argument.
 *
 * @param <T> event type
 * @param <A> argument type
 */
@FunctionalInterface
public interface EventTranslatorOneArg<T, A> {

    void translateTo(T event, long sequence, A arg0);
}
