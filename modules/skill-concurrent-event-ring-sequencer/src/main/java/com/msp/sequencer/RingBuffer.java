package com.msp.sequencer;

import java.util.Objects;

/**
 * RingBuffer holding pre-allocated event objects with single-producer sequencer.
 *
 * @param <T> event type
 */
public final class RingBuffer<T> {

    private final Object[] entries;
    private final int mask;
    private final RingSequencer sequencer;

    @SuppressWarnings("unchecked")
    private RingBuffer(EventFactory<T> factory, int bufferSize, WaitStrategy waitStrategy) {
        this.sequencer = new RingSequencer(bufferSize, waitStrategy);
        this.mask = bufferSize - 1;
        this.entries = new Object[bufferSize];
        for (int i = 0; i < bufferSize; i++) {
            this.entries[i] = Objects.requireNonNull(factory.newInstance(), "factory created null event");
        }
    }

    public static <E> RingBuffer<E> createSingleProducer(EventFactory<E> factory, int bufferSize, WaitStrategy waitStrategy) {
        return new RingBuffer<>(factory, bufferSize, waitStrategy);
    }

    public static <E> RingBuffer<E> createSingleProducer(EventFactory<E> factory, int bufferSize) {
        return new RingBuffer<>(factory, bufferSize, new BlockingWaitStrategy());
    }

    @SuppressWarnings("unchecked")
    public T get(long sequence) {
        return (T) entries[(int) (sequence & mask)];
    }

    public int getBufferSize() {
        return sequencer.getBufferSize();
    }

    public long getCursor() {
        return sequencer.getCursorValue();
    }

    public RingSequencer getSequencer() {
        return sequencer;
    }

    public void addGatingSequence(Sequence... sequences) {
        sequencer.addGatingSequence(sequences);
    }

    public boolean removeGatingSequence(Sequence sequence) {
        return sequencer.removeGatingSequence(sequence);
    }

    public SequenceBarrier newBarrier(Sequence... sequencesToTrack) {
        return sequencer.newBarrier(sequencesToTrack);
    }

    public long next() {
        return sequencer.next();
    }

    public long claim(int n) {
        return sequencer.claim(n);
    }

    public void publish(long sequence) {
        sequencer.publish(sequence);
    }

    public void publish(long low, long high) {
        sequencer.publish(low, high);
    }

    /**
     * Publishes an event using the provided translator. Zero allocation.
     */
    public void publishEvent(EventTranslator<T> translator) {
        long sequence = sequencer.next();
        try {
            translator.translateTo(get(sequence), sequence);
        } finally {
            sequencer.publish(sequence);
        }
    }

    /**
     * Publishes an event with a single argument using the provided translator. Zero allocation.
     */
    public <A> void publishEvent(EventTranslatorOneArg<T, A> translator, A arg0) {
        long sequence = sequencer.next();
        try {
            translator.translateTo(get(sequence), sequence, arg0);
        } finally {
            sequencer.publish(sequence);
        }
    }
}
