package com.msp.crondiff;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * Result record representing the differential comparison between two cron schedules.
 *
 * @param cronA           first cron expression
 * @param cronB           second cron expression
 * @param bothMatchTimes  occurrences where both schedules trigger simultaneously (Intersection)
 * @param onlyAMatchTimes occurrences where only schedule A triggers
 * @param onlyBMatchTimes occurrences where only schedule B triggers
 */
public record CronDiffResult(
        String cronA,
        String cronB,
        List<LocalDateTime> bothMatchTimes,
        List<LocalDateTime> onlyAMatchTimes,
        List<LocalDateTime> onlyBMatchTimes
) {
    public CronDiffResult {
        bothMatchTimes = bothMatchTimes != null ? Collections.unmodifiableList(bothMatchTimes) : List.of();
        onlyAMatchTimes = onlyAMatchTimes != null ? Collections.unmodifiableList(onlyAMatchTimes) : List.of();
        onlyBMatchTimes = onlyBMatchTimes != null ? Collections.unmodifiableList(onlyBMatchTimes) : List.of();
    }

    /**
     * Calculates the Jaccard similarity overlap percentage:
     * (|A ∩ B| / |A ∪ B|) * 100.0
     *
     * @return overlap percentage between 0.0 and 100.0
     */
    public double overlapPercentage() {
        int both = bothMatchTimes.size();
        int onlyA = onlyAMatchTimes.size();
        int onlyB = onlyBMatchTimes.size();
        int union = both + onlyA + onlyB;

        if (union == 0) {
            return 100.0;
        }
        return Math.round(((double) both / union) * 1000.0) / 10.0; // 1 decimal place
    }

    /**
     * Total occurrences produced by Cron A.
     */
    public int totalA() {
        return bothMatchTimes.size() + onlyAMatchTimes.size();
    }

    /**
     * Total occurrences produced by Cron B.
     */
    public int totalB() {
        return bothMatchTimes.size() + onlyBMatchTimes.size();
    }

    /**
     * Generates a readable summary text of the comparison.
     */
    public String summary() {
        return """
                [Cron Diff Comparison]
                Cron A: %s (Total: %d)
                Cron B: %s (Total: %d)
                Simultaneous Matches (Overlap): %d
                Only Cron A: %d
                Only Cron B: %d
                Overlap Ratio: %.1f%%
                """.formatted(
                cronA, totalA(),
                cronB, totalB(),
                bothMatchTimes.size(),
                onlyAMatchTimes.size(),
                onlyBMatchTimes.size(),
                overlapPercentage()
        );
    }
}
