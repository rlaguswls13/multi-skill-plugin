package com.msp.sequencer;

import java.util.Objects;

/**
 * Barrier for coordinating consumers with the producer cursor and any upstream dependencies.
 */
public final class ProcessingSequenceBarrier implements SequenceBarrier {

    private final WaitStrategy waitStrategy;
    private final Sequence cursor;
    private final Sequence[] dependentSequences;
    private volatile boolean alerted = false;

    public ProcessingSequenceBarrier(WaitStrategy waitStrategy, Sequence cursor, Sequence[] dependentSequences) {
        this.waitStrategy = Objects.requireNonNull(waitStrategy, "waitStrategy cannot be null");
        this.cursor = Objects.requireNonNull(cursor, "cursor cannot be null");
        this.dependentSequences = dependentSequences == null ? new Sequence[0] : dependentSequences;
    }

    @Override
    public long waitFor(long sequence) throws InterruptedException, AlertException {
        checkAlert();
        return waitStrategy.waitFor(sequence, cursor, dependentSequences, this);
    }

    @Override
    public long getCursor() {
        return cursor.get();
    }

    @Override
    public boolean isAlerted() {
        return alerted;
    }

    @Override
    public void alert() {
        alerted = true;
        waitStrategy.signalAllWhenBlocking();
    }

    @Override
    public void clearAlert() {
        alerted = false;
    }

    @Override
    public void checkAlert() throws AlertException {
        if (alerted) {
            throw AlertException.INSTANCE;
        }
    }
}
