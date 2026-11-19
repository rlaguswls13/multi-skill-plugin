package com.msp.vegaslimit;

/**
 * Gradient RTT adaptive concurrency control algorithm.
 * <p>
 * Compares the current (short-window) RTT against a slow EWMA baseline ({@code longRtt}).
 * When {@code rttTolerance * longRtt / rtt < 1.0}, the concurrency limit scales down proportionally
 * (bounded below by 0.5 per step); when latency is healthy, the limit grows gradually.
 */
public final class GradientAlgorithm implements LimitAlgorithm {

    private final int minLimit;
    private final int maxLimit;
    private final double rttTolerance;
    private final double smoothing;
    private final double backoffRatio;
    private final int longWindow;
    private final double queueAllowance;

    private double estimatedLimit;
    private double longRttNanos;
    private long minRttNanos;
    private long lastRttNanos;
    private double lastQueueSize;
    private long sampleCount;

    private GradientAlgorithm(Builder b) {
        if (b.minLimit < 1) {
            throw new IllegalArgumentException("minLimit must be >= 1");
        }
        if (b.maxLimit < b.minLimit) {
            throw new IllegalArgumentException("maxLimit must be >= minLimit");
        }
        if (b.initialLimit < b.minLimit || b.initialLimit > b.maxLimit) {
            throw new IllegalArgumentException("initialLimit must be within [minLimit, maxLimit]");
        }
        if (b.rttTolerance < 1.0) {
            throw new IllegalArgumentException("rttTolerance must be >= 1.0");
        }
        if (b.smoothing <= 0.0 || b.smoothing > 1.0) {
            throw new IllegalArgumentException("smoothing must be in (0, 1]");
        }
        if (b.backoffRatio <= 0.0 || b.backoffRatio >= 1.0) {
            throw new IllegalArgumentException("backoffRatio must be in (0, 1)");
        }
        if (b.longWindow < 2) {
            throw new IllegalArgumentException("longWindow must be >= 2");
        }
        this.minLimit = b.minLimit;
        this.maxLimit = b.maxLimit;
        this.rttTolerance = b.rttTolerance;
        this.smoothing = b.smoothing;
        this.backoffRatio = b.backoffRatio;
        this.longWindow = b.longWindow;
        this.queueAllowance = b.queueAllowance;
        this.estimatedLimit = b.initialLimit;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public int currentLimit() {
        return Math.max(minLimit, Math.min(maxLimit, (int) Math.round(estimatedLimit)));
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

    public double longRttNanos() {
        return longRttNanos;
    }

    @Override
    public int onSample(long rttNanos, int inFlight, boolean didDrop) {
        long safeRtt = Math.max(1L, rttNanos);
        this.lastRttNanos = safeRtt;
        if (minRttNanos == 0L || safeRtt < minRttNanos) {
            minRttNanos = safeRtt;
        }

        sampleCount++;
        if (sampleCount == 1) {
            longRttNanos = safeRtt;
        }

        double gradient = Math.max(0.5, Math.min(1.0, (rttTolerance * longRttNanos) / (double) safeRtt));
        this.lastQueueSize = Math.max(0.0, estimatedLimit * (1.0 - gradient));

        double newLimit;
        if (didDrop) {
            newLimit = Math.min(estimatedLimit * backoffRatio, estimatedLimit - 1.0);
        } else if (gradient >= 1.0) {
            newLimit = estimatedLimit + queueAllowance;
        } else {
            newLimit = estimatedLimit * gradient;
        }

        double smoothed = estimatedLimit * (1.0 - smoothing) + newLimit * smoothing;
        this.estimatedLimit = Math.max(minLimit, Math.min(maxLimit, smoothed));

        // Update slow EWMA baseline (cap spike contribution to 2x longRtt so spikes don't mask queuing)
        double cappedRtt = Math.min((double) safeRtt, longRttNanos * 2.0);
        double ewmaAlpha = 2.0 / (longWindow + 1.0);
        longRttNanos = longRttNanos * (1.0 - ewmaAlpha) + cappedRtt * ewmaAlpha;

        return currentLimit();
    }

    public static final class Builder {
        private int initialLimit = 20;
        private int minLimit = 1;
        private int maxLimit = 200;
        private double rttTolerance = 1.5;
        private double smoothing = 0.5;
        private double backoffRatio = 0.9;
        private int longWindow = 100;
        private double queueAllowance = 1.0;

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

        public Builder rttTolerance(double rttTolerance) {
            this.rttTolerance = rttTolerance;
            return this;
        }

        public Builder smoothing(double smoothing) {
            this.smoothing = smoothing;
            return this;
        }

        public Builder backoffRatio(double backoffRatio) {
            this.backoffRatio = backoffRatio;
            return this;
        }

        public Builder longWindow(int longWindow) {
            this.longWindow = longWindow;
            return this;
        }

        public Builder queueAllowance(double queueAllowance) {
            this.queueAllowance = queueAllowance;
            return this;
        }

        public GradientAlgorithm build() {
            return new GradientAlgorithm(this);
        }
    }
}
