package com.msp.jittervis;

import java.util.Objects;
import java.util.OptionalLong;

/**
 * Configuration parameters for cron execution jitter simulation.
 */
public record JitterSimulationConfig(
        int workerCount,
        long maxJitterMs,
        JitterStrategy strategy,
        int histogramBins,
        Long randomSeed
) {
    public static final int DEFAULT_WORKERS = 1000;
    public static final long DEFAULT_MAX_JITTER_MS = 30000L; // 30 seconds
    public static final JitterStrategy DEFAULT_STRATEGY = JitterStrategy.FULL_JITTER;
    public static final int DEFAULT_BINS = 10;

    public JitterSimulationConfig {
        if (workerCount <= 0) {
            throw new IllegalArgumentException("workerCount must be positive");
        }
        if (maxJitterMs < 0) {
            throw new IllegalArgumentException("maxJitterMs cannot be negative");
        }
        strategy = strategy != null ? strategy : DEFAULT_STRATEGY;
        histogramBins = histogramBins > 0 ? histogramBins : DEFAULT_BINS;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private int workerCount = DEFAULT_WORKERS;
        private long maxJitterMs = DEFAULT_MAX_JITTER_MS;
        private JitterStrategy strategy = DEFAULT_STRATEGY;
        private int histogramBins = DEFAULT_BINS;
        private Long randomSeed = null;

        public Builder workerCount(int count) {
            this.workerCount = count;
            return this;
        }

        public Builder maxJitterMs(long maxMs) {
            this.maxJitterMs = maxMs;
            return this;
        }

        public Builder strategy(JitterStrategy strategy) {
            this.strategy = strategy;
            return this;
        }

        public Builder histogramBins(int bins) {
            this.histogramBins = bins;
            return this;
        }

        public Builder randomSeed(long seed) {
            this.randomSeed = seed;
            return this;
        }

        public JitterSimulationConfig build() {
            return new JitterSimulationConfig(workerCount, maxJitterMs, strategy, histogramBins, randomSeed);
        }
    }
}
