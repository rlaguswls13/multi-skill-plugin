package com.msp.jittervis;

import java.util.*;

/**
 * High-performance simulator and analyzer for cron execution jitter dispersion.
 */
public final class JitterSimulator {

    private JitterSimulator() {
    }

    /**
     * Executes a jitter simulation based on the provided configuration.
     *
     * @param config simulation parameters
     * @return complete simulation result including metrics and histogram bins
     */
    public static JitterSimulationResult simulate(JitterSimulationConfig config) {
        Objects.requireNonNull(config, "config cannot be null");

        Random random = config.randomSeed() != null ? new Random(config.randomSeed()) : new Random();
        int n = config.workerCount();
        long maxJitter = config.maxJitterMs();
        long[] delays = new long[n];

        double sum = 0;
        for (int i = 0; i < n; i++) {
            delays[i] = config.strategy().computeDelay(maxJitter, random);
            sum += delays[i];
        }

        Arrays.sort(delays);

        long minDelay = delays[0];
        long maxDelay = delays[n - 1];
        double mean = sum / n;

        double median = (n % 2 != 0) ? delays[n / 2] : (delays[n / 2 - 1] + delays[n / 2]) / 2.0;

        double varianceSum = 0;
        for (long d : delays) {
            double diff = d - mean;
            varianceSum += diff * diff;
        }
        double variance = varianceSum / n;
        double stdDev = Math.sqrt(variance);
        double cv = mean > 0 ? stdDev / mean : 0.0;

        // Build histogram bins
        int numBins = config.histogramBins();
        List<HistogramBin> bins = new ArrayList<>(numBins);
        double binWidth = maxJitter > 0 ? (double) maxJitter / numBins : 1.0;

        int peakConcurrency = 0;
        int currentDelayIdx = 0;

        for (int b = 0; b < numBins; b++) {
            long startMs = Math.round(b * binWidth);
            long endMs = (b == numBins - 1) ? maxJitter : Math.round((b + 1) * binWidth);

            int count = 0;
            while (currentDelayIdx < n) {
                long d = delays[currentDelayIdx];
                if (b == numBins - 1 ? (d >= startMs && d <= endMs) : (d >= startMs && d < endMs)) {
                    count++;
                    currentDelayIdx++;
                } else if (d < startMs) {
                    currentDelayIdx++;
                } else {
                    break;
                }
            }

            if (count > peakConcurrency) {
                peakConcurrency = count;
            }

            double percentage = ((double) count / n) * 100.0;
            bins.add(new HistogramBin(startMs, endMs, count, percentage));
        }

        int peakNoJitter = n;
        double reductionPercent = 100.0 * (1.0 - ((double) peakConcurrency / peakNoJitter));

        JitterStatistics stats = new JitterStatistics(
                minDelay,
                maxDelay,
                mean,
                median,
                variance,
                stdDev,
                cv,
                peakNoJitter,
                peakConcurrency,
                reductionPercent
        );

        return new JitterSimulationResult(config, delays, stats, bins);
    }

    /**
     * Runs simulations for all available jitter strategies and generates a comparative ASCII summary table.
     */
    public static String compareStrategies(int workerCount, long maxJitterMs, Long seed) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.ROOT, "=== Jitter Strategy Comparison (Workers=%,d, MaxJitter=%,d ms) ===\n",
                workerCount, maxJitterMs));
        sb.append(String.format("%-14s | %-12s | %-12s | %-8s | %-14s | %-14s\n",
                "Strategy", "Mean Delay", "StdDev (σ)", "CV", "Peak Concur.", "Collision Red."));
        sb.append("------------------------------------------------------------------------------------\n");

        for (JitterStrategy strat : JitterStrategy.values()) {
            JitterSimulationConfig cfg = JitterSimulationConfig.builder()
                    .workerCount(workerCount)
                    .maxJitterMs(maxJitterMs)
                    .strategy(strat)
                    .histogramBins(10)
                    .randomSeed(seed != null ? seed : 42L)
                    .build();

            JitterSimulationResult res = simulate(cfg);
            JitterStatistics s = res.statistics();

            sb.append(String.format(Locale.ROOT, "%-14s | %,9.1f ms | %,9.1f ms | %8.4f | %,10d wkrs | %12.2f %%\n",
                    strat.name(), s.meanDelayMs(), s.stdDevMs(), s.coefficientOfVariation(),
                    s.peakConcurrencyWithJitter(), s.collisionReductionPercent()));
        }
        sb.append("====================================================================================\n");
        return sb.toString();
    }
}
