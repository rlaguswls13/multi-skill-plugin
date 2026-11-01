package com.msp.jittervis;

import java.util.Random;

/**
 * Jitter dispersion strategies for dispersing concurrent cron worker executions.
 */
public enum JitterStrategy {

    /**
     * Uniform distribution across [0, maxJitterMs).
     */
    UNIFORM {
        @Override
        public long computeDelay(long maxJitterMs, Random random) {
            if (maxJitterMs <= 0) return 0;
            return (long) (random.nextDouble() * maxJitterMs);
        }
    },

    /**
     * AWS Full Jitter: Sleep = random_between(0, maxJitter).
     */
    FULL_JITTER {
        @Override
        public long computeDelay(long maxJitterMs, Random random) {
            if (maxJitterMs <= 0) return 0;
            return (long) (random.nextDouble() * maxJitterMs);
        }
    },

    /**
     * AWS Equal Jitter: Sleep = (maxJitter / 2) + random_between(0, maxJitter / 2).
     * Guarantees a minimum non-zero dispersion delay while spreading out spikes.
     */
    EQUAL_JITTER {
        @Override
        public long computeDelay(long maxJitterMs, Random random) {
            if (maxJitterMs <= 0) return 0;
            long half = maxJitterMs / 2;
            return half + (long) (random.nextDouble() * (maxJitterMs - half));
        }
    },

    /**
     * Gaussian / Normal distribution centered at (maxJitter / 2) with 99.7% of values
     * within [0, maxJitterMs].
     */
    GAUSSIAN {
        @Override
        public long computeDelay(long maxJitterMs, Random random) {
            if (maxJitterMs <= 0) return 0;
            double mean = maxJitterMs / 2.0;
            double stdDev = maxJitterMs / 6.0;
            double val = mean + random.nextGaussian() * stdDev;
            long clamped = Math.round(val);
            if (clamped < 0) return 0;
            if (clamped > maxJitterMs) return maxJitterMs;
            return clamped;
        }
    };

    public abstract long computeDelay(long maxJitterMs, Random random);
}
