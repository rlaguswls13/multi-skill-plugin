package com.msp.signotifier;

/**
 * Standard POSIX OS signals supported for graceful notification and shutdown handling.
 */
public enum SignalType {
    /**
     * Interactive attention signal (e.g. Ctrl+C in terminal).
     */
    SIGINT(2),

    /**
     * Termination request signal (standard kill / container stop).
     */
    SIGTERM(15),

    /**
     * Hangup detected on controlling terminal or configuration reload request.
     */
    SIGHUP(1),

    /**
     * Interactive quit signal (e.g. Ctrl+\\ in terminal).
     */
    SIGQUIT(3);

    private final int signalNumber;

    SignalType(int signalNumber) {
        this.signalNumber = signalNumber;
    }

    public int getSignalNumber() {
        return signalNumber;
    }

    public boolean isTermination() {
        return this == SIGINT || this == SIGTERM || this == SIGQUIT;
    }
}
