package com.msp.sequencer;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Event consumer loop that processes events in batches from the RingBuffer.
 *
 * @param <T> event type
 */
public final class BatchEventProcessor<T> implements Runnable {

    private final RingBuffer<T> ringBuffer;
    private final SequenceBarrier sequenceBarrier;
    private final EventHandler<T> eventHandler;
    private final Sequence sequence = new Sequence(Sequence.INITIAL_VALUE);
    private final AtomicBoolean running = new AtomicBoolean(false);

    public BatchEventProcessor(RingBuffer<T> ringBuffer, SequenceBarrier sequenceBarrier, EventHandler<T> eventHandler) {
        this.ringBuffer = Objects.requireNonNull(ringBuffer, "ringBuffer cannot be null");
        this.sequenceBarrier = Objects.requireNonNull(sequenceBarrier, "sequenceBarrier cannot be null");
        this.eventHandler = Objects.requireNonNull(eventHandler, "eventHandler cannot be null");
    }

    public Sequence getSequence() {
        return sequence;
    }

    public boolean isRunning() {
        return running.get();
    }

    public void halt() {
        running.set(false);
        sequenceBarrier.alert();
    }

    @Override
    public void run() {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("BatchEventProcessor is already running");
        }
        sequenceBarrier.clearAlert();

        long nextSequence = sequence.get() + 1L;

        while (running.get()) {
            try {
                long availableSequence = sequenceBarrier.waitFor(nextSequence);

                while (nextSequence <= availableSequence) {
                    T event = ringBuffer.get(nextSequence);
                    eventHandler.onEvent(event, nextSequence, nextSequence == availableSequence);
                    nextSequence++;
                }

                sequence.set(availableSequence);
            } catch (AlertException ex) {
                if (!running.get()) {
                    break;
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            } catch (Throwable t) {
                sequence.set(nextSequence);
                nextSequence++;
            }
        }
    }
}
