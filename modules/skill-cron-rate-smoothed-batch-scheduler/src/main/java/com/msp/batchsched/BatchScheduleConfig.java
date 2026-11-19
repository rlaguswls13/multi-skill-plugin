package com.msp.batchsched;

import java.util.Objects;

/**
 * Configuration governing slot sizing, concurrency cap, deterministic jitter, and spread strategy.
 */
public record BatchScheduleConfig(
        int maxBatchSize,
        int maxConcurrency,
        double jitterRatio,
        long jitterSeed,
        SpreadStrategy spreadStrategy
) {

    public BatchScheduleConfig {
        if (maxBatchSize <= 0) {
            throw new IllegalArgumentException("maxBatchSize must be > 0: " + maxBatchSize);
        }
        if (maxConcurrency <= 0) {
            throw new IllegalArgumentException("maxConcurrency must be > 0: " + maxConcurrency);
        }
        if (!Double.isFinite(jitterRatio) || jitterRatio < 0.0 || jitterRatio > 0.5) {
            throw new IllegalArgumentException("jitterRatio must be in [0.0, 0.5]: " + jitterRatio);
        }
        Objects.requireNonNull(spreadStrategy, "spreadStrategy must not be null");
    }

    public static BatchScheduleConfig defaults() {
        return new BatchScheduleConfig(1, 1, 0.0, 0L, SpreadStrategy.PRESERVE_ORDER);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private int maxBatchSize = 1;
        private int maxConcurrency = 1;
        private double jitterRatio = 0.0;
        private long jitterSeed = 0L;
        private SpreadStrategy spreadStrategy = SpreadStrategy.PRESERVE_ORDER;

        private Builder() {
        }

        public Builder maxBatchSize(int maxBatchSize) {
            this.maxBatchSize = maxBatchSize;
            return this;
        }

        public Builder maxConcurrency(int maxConcurrency) {
            this.maxConcurrency = maxConcurrency;
            return this;
        }

        public Builder jitterRatio(double jitterRatio) {
            this.jitterRatio = jitterRatio;
            return this;
        }

        public Builder jitterSeed(long jitterSeed) {
            this.jitterSeed = jitterSeed;
            return this;
        }

        public Builder spreadStrategy(SpreadStrategy spreadStrategy) {
            this.spreadStrategy = spreadStrategy;
            return this;
        }

        public BatchScheduleConfig build() {
            return new BatchScheduleConfig(maxBatchSize, maxConcurrency, jitterRatio, jitterSeed, spreadStrategy);
        }
    }
}
