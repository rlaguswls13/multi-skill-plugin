package com.msp.sequencer;

/**
 * Exception thrown when a SequenceBarrier is alerted, signaling consumer loop termination.
 */
public final class AlertException extends Exception {

    public static final AlertException INSTANCE = new AlertException();

    private AlertException() {
        super("Sequence barrier alerted", null, false, false);
    }
}
