package com.msp.jittervis;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Result of a jitter simulation containing raw delays, statistical metrics, and ASCII visualization methods.
 */
public record JitterSimulationResult(
        JitterSimulationConfig config,
        long[] delays,
        JitterStatistics statistics,
        List<HistogramBin> histogram
) {
    public JitterSimulationResult {
        histogram = histogram != null ? List.copyOf(histogram) : Collections.emptyList();
    }

    /**
     * Renders a clean ASCII horizontal bar histogram representing the execution dispersion.
     *
     * @param maxBarWidth maximum width in characters for the largest bar (e.g. 40)
     * @return multi-line ASCII histogram string
     */
    public String renderHistogram(int maxBarWidth) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("=== Jitter Distribution Histogram (%s, Workers=%d, MaxJitter=%dms) ===\n",
                config.strategy(), config.workerCount(), config.maxJitterMs()));

        int maxCount = 0;
        for (HistogramBin bin : histogram) {
            if (bin.count() > maxCount) {
                maxCount = bin.count();
            }
        }
        if (maxCount == 0) maxCount = 1;

        int width = maxBarWidth > 0 ? maxBarWidth : 40;

        for (HistogramBin bin : histogram) {
            int barLen = (int) Math.round(((double) bin.count() / maxCount) * width);
            String bar = "█".repeat(Math.max(0, barLen));

            sb.append(String.format(Locale.ROOT, "[%7dms - %7dms]: %-40s %5d (%5.1f%%)\n",
                    bin.rangeStartMs(), bin.rangeEndMs(), bar, bin.count(), bin.percentage()));
        }

        return sb.toString();
    }

    /**
     * Renders a human-readable ASCII summary table containing statistical dispersion metrics.
     */
    public String renderReportTable() {
        StringBuilder sb = new StringBuilder();
        sb.append("========================================================================\n");
        sb.append(String.format(Locale.ROOT, " Cron Execution Jitter Analysis Report - Strategy: %s\n", config.strategy()));
        sb.append("========================================================================\n");
        sb.append(String.format(Locale.ROOT, "  Workers Simulated           : %,d\n", config.workerCount()));
        sb.append(String.format(Locale.ROOT, "  Max Configured Jitter       : %,d ms\n", config.maxJitterMs()));
        sb.append(String.format(Locale.ROOT, "  Min Delay Observed          : %,d ms\n", statistics.minDelayMs()));
        sb.append(String.format(Locale.ROOT, "  Max Delay Observed          : %,d ms\n", statistics.maxDelayMs()));
        sb.append(String.format(Locale.ROOT, "  Mean Delay                  : %,.2f ms\n", statistics.meanDelayMs()));
        sb.append(String.format(Locale.ROOT, "  Median Delay                : %,.2f ms\n", statistics.medianDelayMs()));
        sb.append(String.format(Locale.ROOT, "  Standard Deviation (σ)      : %,.2f ms\n", statistics.stdDevMs()));
        sb.append(String.format(Locale.ROOT, "  Coefficient of Variation    : %,.4f\n", statistics.coefficientOfVariation()));
        sb.append("------------------------------------------------------------------------\n");
        sb.append(String.format(Locale.ROOT, "  Peak Concurrency (No Jitter): %,d workers\n", statistics.peakConcurrencyWithoutJitter()));
        sb.append(String.format(Locale.ROOT, "  Peak Concurrency (Jittered) : %,d workers\n", statistics.peakConcurrencyWithJitter()));
        sb.append(String.format(Locale.ROOT, "  Collision Reduction Rate    : %,.2f %%\n", statistics.collisionReductionPercent()));
        sb.append("========================================================================\n");
        return sb.toString();
    }
}
