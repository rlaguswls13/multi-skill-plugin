package com.msp.sequencer;

/**
 * Busy-spin wait strategy that uses Thread.onSpinWait() for lowest latency at high CPU utilization.
 */
public final class BusySpinWaitStrategy implements WaitStrategy {

    @Override
    public long waitFor(long sequence, Sequence cursor, Sequence[] dependentSequences, SequenceBarrier barrier)
            throws InterruptedException, AlertException {
        long availableSequence;

        if (dependentSequences.length == 0) {
            while ((availableSequence = cursor.get()) < sequence) {
                barrier.checkAlert();
                Thread.onSpinWait();
            }
        } else {
            while ((availableSequence = getMinimumSequence(dependentSequences)) < sequence) {
                barrier.checkAlert();
                Thread.onSpinWait();
            }
        }

        return availableSequence;
    }

    @Override
    public void signalAllWhenBlocking() {
        // No-op for spin wait
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
