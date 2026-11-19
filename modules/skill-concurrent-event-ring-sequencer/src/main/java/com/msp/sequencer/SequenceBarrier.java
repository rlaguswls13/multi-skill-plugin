package com.msp.sequencer;

/**
 * Coordination barrier for consumer threads to track available sequences and alert status.
 */
public interface SequenceBarrier {

    /**
     * Wait for the given sequence to be available.
     */
    long waitFor(long sequence) throws InterruptedException, AlertException;

    /**
     * Get the current cursor sequence.
     */
    long getCursor();

    /**
     * Check if the barrier has been alerted.
     */
    boolean isAlerted();

    /**
     * Alert waiting consumers to abort waiting.
     */
    void alert();

    /**
     * Clear alert status.
     */
    void clearAlert();

    /**
     * Check if alerted and throw AlertException if so.
     */
    void checkAlert() throws AlertException;
}
