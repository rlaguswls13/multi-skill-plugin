package com.msp.sequencer;

/**
 * Yielding wait strategy: spins for a fixed number of iterations, then calls Thread.yield().
 * Provides a good trade-off between latency and CPU usage without kernel context switches.
 */
public final class YieldingWaitStrategy implements WaitStrategy {

    private static final int DEFAULT_SPIN_TRIES = 100;
    private final int spinTries;

    public YieldingWaitStrategy() {
        this(DEFAULT_SPIN_TRIES);
    }

    public YieldingWaitStrategy(int spinTries) {
        if (spinTries <= 0) {
            throw new IllegalArgumentException("spinTries must be positive");
        }
        this.spinTries = spinTries;
    }

    @Override
    public long waitFor(long sequence, Sequence cursor, Sequence[] dependentSequences, SequenceBarrier barrier)
            throws InterruptedException, AlertException {
        long availableSequence;
        int counter = spinTries;

        if (dependentSequences.length == 0) {
            while ((availableSequence = cursor.get()) < sequence) {
                counter = applyWait(barrier, counter);
            }
        } else {
            while ((availableSequence = getMinimumSequence(dependentSequences)) < sequence) {
                counter = applyWait(barrier, counter);
            }
        }

        return availableSequence;
    }

    private int applyWait(SequenceBarrier barrier, int counter) throws AlertException {
        barrier.checkAlert();
        if (counter > 0) {
            counter--;
            Thread.onSpinWait();
        } else {
            Thread.yield();
        }
        return counter;
    }

    @Override
    public void signalAllWhenBlocking() {
        // No-op for yielding
    }

    private static long getMinimumSequence(Sequence[] sequences) {
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
