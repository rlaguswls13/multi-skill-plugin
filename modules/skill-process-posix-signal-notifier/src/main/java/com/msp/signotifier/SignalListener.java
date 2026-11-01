package com.msp.signotifier;

/**
 * Functional callback interface for handling POSIX signals and JVM shutdown events.
 */
@FunctionalInterface
public interface SignalListener {

    /**
     * Invoked when a signal is dispatched.
     *
     * @param event details about the received signal
     * @throws Exception any exception during shutdown / reload processing
     */
    void onSignal(SignalEvent event) throws Exception;
}
