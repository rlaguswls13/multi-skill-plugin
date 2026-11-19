package com.msp.vegaslimit;

/**
 * TCP Vegas adaptive concurrency control algorithm.
 * <p>
 * Estimates queuing depth using {@code queueSize = limit * (1 - minRtt / rtt)}:
 * <ul>
 *   <li>If {@code didDrop} is true: backs off concurrency limit by {@code backoffRatio}.</li>
 *   <li>If {@code queueSize <= alpha}: increases concurrency limit.</li>
 *   <li>If {@code queueSize >= beta}: decreases concurrency limit.</li>
 *   <li>Otherwise holds the current limit steady.</li>
 * </ul>
 */
public final class VegasAlgorithm implements LimitAlgorithm {

    private final int minLimit;
    private final int maxLimit;
    private final double alpha;
    private final double beta;
    private final double backoffRatio;
    private final double smoothing;
    private final int probeInterval;

    private double estimatedLimit;
    private long minRttNanos;
    private long lastRttNanos;
    private double lastQueueSize;
    private int probeCount;

    private VegasAlgorithm(Builder b) {
        if (b.minLimit < 1) {
            throw new IllegalArgumentException("minLimit must be >= 1");
        }
        if (b.maxLimit < b.minLimit) {
            throw new IllegalArgumentException("maxLimit must be >= minLimit");
        }
        if (b.initialLimit < b.minLimit || b.initialLimit > b.maxLimit) {
            throw new IllegalArgumentException("initialLimit must be within [minLimit, maxLimit]");
        }
        if (b.alpha < 0 || b.beta < b.alpha) {
            throw new IllegalArgumentException("alpha must be >= 0 and beta >= alpha");
        }
        if (b.backoffRatio <= 0.0 || b.backoffRatio >= 1.0) {
            throw new IllegalArgumentException("backoffRatio must be in (0, 1)");
        }
        if (b.smoothing <= 0.0 || b.smoothing > 1.0) {
            throw new IllegalArgumentException("smoothing must be in (0, 1]");
        }
        if (b.probeInterval < 1) {
            throw new IllegalArgumentException("probeInterval must be >= 1");
        }
        this.minLimit = b.minLimit;
        this.maxLimit = b.maxLimit;
        this.alpha = b.alpha;
        this.beta = b.beta;
        this.backoffRatio = b.backoffRatio;
        this.smoothing = b.smoothing;
        this.probeInterval = b.probeInterval;
        this.estimatedLimit = b.initialLimit;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public int currentLimit() {
        return clamp((int) Math.round(estimatedLimit));
    }

    @Override
    public int minLimit() {
        return minLimit;
    }

    @Override
    public int maxLimit() {
        return maxLimit;
    }

    @Override
    public long minRttNanos() {
        return minRttNanos;
    }

    @Override
    public long lastRttNanos() {
        return lastRttNanos;
    }

    @Override
    public double estimatedQueueSize() {
        return lastQueueSize;
    }

    @Override
    public int onSample(long rttNanos, int inFlight, boolean didDrop) {
        long safeRtt = Math.max(1L, rttNanos);
        this.lastRttNanos = safeRtt;

        probeCount++;
        if (minRttNanos == 0L || safeRtt < minRttNanos) {
            minRttNanos = safeRtt;
            probeCount = 0;
        } else if (probeCount >= probeInterval) {
            // Periodically allow minRtt to adapt upward if baseline latency permanently shifts
            minRttNanos = safeRtt;
            probeCount = 0;
        }

        double queueSize = estimatedLimit * (1.0 - ((double) minRttNanos / (double) safeRtt));
        this.lastQueueSize = Math.max(0.0, queueSize);

        double newLimit = estimatedLimit;
        if (didDrop) {
            double backedOff = estimatedLimit * backoffRatio;
            newLimit = Math.min(backedOff, estimatedLimit - 1.0);
        } else if (queueSize <= alpha) {
            newLimit = estimatedLimit + 1.0;
        } else if (queueSize >= beta) {
            newLimit = estimatedLimit - 1.0;
        }

        double smoothed = estimatedLimit * (1.0 - smoothing) + newLimit * smoothing;
        this.estimatedLimit = Math.max(minLimit, Math.min(maxLimit, smoothed));
        return currentLimit();
    }

    private int clamp(int value) {
        return Math.max(minLimit, Math.min(maxLimit, value));
    }

    public static final class Builder {
        private int initialLimit = 20;
        private int minLimit = 1;
        private int maxLimit = 200;
        private double alpha = 3.0;
        private double beta = 6.0;
        private double backoffRatio = 0.9;
        private double smoothing = 1.0;
        private int probeInterval = 1_000;

        public Builder initialLimit(int initialLimit) {
            this.initialLimit = initialLimit;
            return this;
        }

        public Builder minLimit(int minLimit) {
            this.minLimit = minLimit;
            return this;
        }

        public Builder maxLimit(int maxLimit) {
            this.maxLimit = maxLimit;
            return this;
        }

        public Builder alpha(double alpha) {
            this.alpha = alpha;
            return this;
        }

        public Builder beta(double beta) {
            this.beta = beta;
            return this;
        }

        public Builder backoffRatio(double backoffRatio) {
            this.backoffRatio = backoffRatio;
            return this;
        }

        public Builder smoothing(double smoothing) {
            this.smoothing = smoothing;
            return this;
        }

        public Builder probeInterval(int probeInterval) {
            this.probeInterval = probeInterval;
            return this;
        }

        public VegasAlgorithm build() {
            return new VegasAlgorithm(this);
        }
    }
}
