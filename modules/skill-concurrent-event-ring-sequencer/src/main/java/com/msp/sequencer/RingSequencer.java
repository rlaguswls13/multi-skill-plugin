package com.msp.sequencer;

import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.locks.LockSupport;

/**
 * Single-Producer Ring Sequencer coordinating sequence claims, gating sequences, and event publishing.
 */
public final class RingSequencer {

    private final int bufferSize;
    private final Sequence cursor = new Sequence(Sequence.INITIAL_VALUE);
    private final Sequence claimedSequence = new Sequence(Sequence.INITIAL_VALUE);
    private volatile Sequence[] gatingSequences = new Sequence[0];
    private final WaitStrategy waitStrategy;

    public RingSequencer(int bufferSize, WaitStrategy waitStrategy) {
        if (bufferSize < 1) {
            throw new IllegalArgumentException("bufferSize must not be less than 1");
        }
        if (Integer.bitCount(bufferSize) != 1) {
            throw new IllegalArgumentException("bufferSize must be a power of 2");
        }
        this.bufferSize = bufferSize;
        this.waitStrategy = Objects.requireNonNull(waitStrategy, "waitStrategy cannot be null");
    }

    public int getBufferSize() {
        return bufferSize;
    }

    public Sequence getCursor() {
        return cursor;
    }

    public long getCursorValue() {
        return cursor.get();
    }

    public WaitStrategy getWaitStrategy() {
        return waitStrategy;
    }

    /**
     * Add gating sequences that producer must not overwrite.
     */
    public synchronized void addGatingSequence(Sequence... sequences) {
        if (sequences == null || sequences.length == 0) return;
        Sequence[] updated = Arrays.copyOf(gatingSequences, gatingSequences.length + sequences.length);
        System.arraycopy(sequences, 0, updated, gatingSequences.length, sequences.length);
        this.gatingSequences = updated;
    }

    /**
     * Remove a gating sequence.
     */
    public synchronized boolean removeGatingSequence(Sequence sequence) {
        int index = -1;
        for (int i = 0; i < gatingSequences.length; i++) {
            if (gatingSequences[i] == sequence) {
                index = i;
                break;
            }
        }
        if (index == -1) return false;

        Sequence[] updated = new Sequence[gatingSequences.length - 1];
        System.arraycopy(gatingSequences, 0, updated, 0, index);
        System.arraycopy(gatingSequences, index + 1, updated, index, gatingSequences.length - index - 1);
        this.gatingSequences = updated;
        return true;
    }

    public Sequence[] getGatingSequences() {
        return gatingSequences;
    }

    public long getMinimumGatingSequence() {
        return getMinimumSequence(gatingSequences);
    }

    /**
     * Claim next n sequences. Returns the highest claimed sequence.
     * The range of claimed sequences is: [highest - n + 1, highest].
     */
    public long claim(int n) {
        if (n < 1) {
            throw new IllegalArgumentException("n must be > 0");
        }

        long currentClaim = claimedSequence.get();
        long nextClaim = currentClaim + n;
        long wrapPoint = nextClaim - bufferSize;

        long cachedGatingSequence = getMinimumSequence(gatingSequences);

        while (wrapPoint > cachedGatingSequence) {
            LockSupport.parkNanos(1L);
            cachedGatingSequence = getMinimumSequence(gatingSequences);
        }

        claimedSequence.set(nextClaim);
        return nextClaim;
    }

    /**
     * Claim the single next sequence.
     */
    public long next() {
        return claim(1);
    }

    /**
     * Publish a single sequence.
     */
    public void publish(long sequence) {
        cursor.setRelease(sequence);
        waitStrategy.signalAllWhenBlocking();
    }

    /**
     * Publish a batch of sequences from low to high.
     */
    public void publish(long low, long high) {
        cursor.setRelease(high);
        waitStrategy.signalAllWhenBlocking();
    }

    /**
     * Create a new barrier for consumers to track the published cursor and any upstream dependencies.
     */
    public SequenceBarrier newBarrier(Sequence... sequencesToTrack) {
        return new ProcessingSequenceBarrier(waitStrategy, cursor, sequencesToTrack);
    }

    private static long getMinimumSequence(Sequence[] sequences) {
        if (sequences.length == 0) {
            return Long.MAX_VALUE;
        }
        long min = Long.MAX_VALUE;
        for (Sequence seq : sequences) {
            long val = seq.get();
            if (val < min) {
                min = val;
            }
        }
        return min;
    }
}
