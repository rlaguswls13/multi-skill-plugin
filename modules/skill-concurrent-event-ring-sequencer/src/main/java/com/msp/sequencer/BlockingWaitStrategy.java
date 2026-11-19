package com.msp.sequencer;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Blocking wait strategy using ReentrantLock and Condition.
 * Most conservative strategy with minimum CPU usage when idle.
 */
public final class BlockingWaitStrategy implements WaitStrategy {

    private final Lock lock = new ReentrantLock();
    private final Condition processorNotifyCondition = lock.newCondition();

    @Override
    public long waitFor(long sequence, Sequence cursor, Sequence[] dependentSequences, SequenceBarrier barrier)
            throws InterruptedException, AlertException {
        long availableSequence;

        if (cursor.get() < sequence) {
            lock.lock();
            try {
                while (cursor.get() < sequence) {
                    barrier.checkAlert();
                    processorNotifyCondition.await();
                }
            } finally {
                lock.unlock();
            }
        }

        if (dependentSequences.length != 0) {
            while ((availableSequence = getMinimumSequence(dependentSequences)) < sequence) {
                barrier.checkAlert();
                Thread.onSpinWait();
            }
            return availableSequence;
        }

        return cursor.get();
    }

    @Override
    public void signalAllWhenBlocking() {
        lock.lock();
        try {
            processorNotifyCondition.signalAll();
        } finally {
            lock.unlock();
        }
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
