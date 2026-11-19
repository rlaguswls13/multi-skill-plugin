package com.msp.vegaslimit;

/**
 * Adaptive concurrency limit control algorithm driven by observed request RTT samples.
 */
public sealed interface LimitAlgorithm permits VegasAlgorithm, GradientAlgorithm {

    int currentLimit();

    int minLimit();

    int maxLimit();

    long minRttNanos();

    long lastRttNanos();

    double estimatedQueueSize();

    /**
     * Feeds a completed or dropped request observation into the algorithm and returns the updated limit.
     *
     * @param rttNanos observed round-trip latency in nanoseconds (must be >= 0)
     * @param inFlight concurrency level observed when the request completed
     * @param didDrop  true if the request was dropped / timed out / rejected downstream
     * @return updated concurrency limit within {@code [minLimit(), maxLimit()]}
     */
    int onSample(long rttNanos, int inFlight, boolean didDrop);
}
