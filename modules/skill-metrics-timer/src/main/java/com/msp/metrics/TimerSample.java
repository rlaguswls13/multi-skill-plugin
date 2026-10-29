package com.msp.metrics;

/**
 * A running timer sample returned by {@link MetricsTimer#start(String)}.
 * Implements {@link AutoCloseable} for convenient try-with-resources profiling.
 */
public interface TimerSample extends AutoCloseable {

    /**
     * Stops this timer sample, records the elapsed duration, and returns the elapsed time in nanoseconds.
     */
    long stop();

    /**
     * Returns the elapsed nanoseconds since this sample was started, without stopping it.
     */
    long elapsedNanos();

    @Override
    default void close() {
        stop();
    }
}
