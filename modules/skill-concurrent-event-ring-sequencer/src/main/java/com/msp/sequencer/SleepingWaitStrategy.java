package com.msp.sequencer;

import java.util.concurrent.locks.LockSupport;

/**
 * Sleeping wait strategy that progresses from spinning to yielding to parkNanos.
 * Good balance of high throughput and lower CPU utilization during quiet periods.
 */
public final class SleepingWaitStrategy implements WaitStrategy {

    private static final int DEFAULT_RETRIES = 200;
    private static final long DEFAULT_SLEEP_NANOS = 100L;

    private final int retries;
    private final long sleepNanos;

    public SleepingWaitStrategy() {
        this(DEFAULT_RETRIES, DEFAULT_SLEEP_NANOS);
    }

    public SleepingWaitStrategy(int retries, long sleepNanos) {
        if (retries <= 0) {
            throw new IllegalArgumentException("retries must be positive");
        }
        if (sleepNanos < 0) {
            throw new IllegalArgumentException("sleepNanos cannot be negative");
        }
        this.retries = retries;
        this.sleepNanos = sleepNanos;
    }

    @Override
    public long waitFor(long sequence, Sequence cursor, Sequence[] dependentSequences, SequenceBarrier barrier)
            throws InterruptedException, AlertException {
        long availableSequence;
        int counter = retries;

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

    private int applyWait(SequenceBarrier barrier, int counter) throws AlertException, InterruptedException {
        barrier.checkAlert();
        if (counter > 100) {
            counter--;
            Thread.onSpinWait();
        } else if (counter > 0) {
            counter--;
            Thread.yield();
        } else {
            LockSupport.parkNanos(sleepNanos);
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }
        }
        return counter;
    }

    @Override
    public void signalAllWhenBlocking() {
        // No-op for sleeping wait strategy
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
