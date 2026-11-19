package com.msp.sequencer;

/**
 * Strategy employed for making event processors wait on a cursor sequence.
 */
public interface WaitStrategy {

    /**
     * Wait for the given sequence to be available for consumption.
     *
     * @param sequence           the sequence to wait for
     * @param cursor             the main cursor sequence
     * @param dependentSequences any dependent/preceding consumer sequences
     * @param barrier            the barrier to check for alerts
     * @return the available sequence (may be greater than or equal to the requested sequence)
     * @throws InterruptedException if thread is interrupted
     * @throws AlertException       if the barrier has been alerted
     */
    long waitFor(long sequence, Sequence cursor, Sequence[] dependentSequences, SequenceBarrier barrier)
            throws InterruptedException, AlertException;

    /**
     * Signal all waiting consumers that sequence state has changed.
     */
    void signalAllWhenBlocking();
}
