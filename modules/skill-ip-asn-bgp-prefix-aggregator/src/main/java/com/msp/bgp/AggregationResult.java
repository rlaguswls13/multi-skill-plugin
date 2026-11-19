package com.msp.bgp;

import java.util.Collections;
import java.util.List;

/**
 * Result of BGP route and CIDR prefix aggregation.
 */
public final class AggregationResult {

    private final List<CidrPrefix> originalPrefixes;
    private final List<CidrPrefix> aggregatedPrefixes;

    public AggregationResult(List<CidrPrefix> originalPrefixes, List<CidrPrefix> aggregatedPrefixes) {
        this.originalPrefixes = Collections.unmodifiableList(originalPrefixes);
        this.aggregatedPrefixes = Collections.unmodifiableList(aggregatedPrefixes);
    }

    public List<CidrPrefix> getOriginalPrefixes() {
        return originalPrefixes;
    }

    public List<CidrPrefix> getAggregatedPrefixes() {
        return aggregatedPrefixes;
    }

    public int originalCount() {
        return originalPrefixes.size();
    }

    public int aggregatedCount() {
        return aggregatedPrefixes.size();
    }

    public double reductionPercentage() {
        if (originalPrefixes.isEmpty()) {
            return 0.0;
        }
        int diff = originalPrefixes.size() - aggregatedPrefixes.size();
        return ((double) diff / originalPrefixes.size()) * 100.0;
    }

    public List<String> toCidrStrings() {
        return aggregatedPrefixes.stream().map(CidrPrefix::toCidrString).toList();
    }

    @Override
    public String toString() {
        return "AggregationResult{" +
                "original=" + originalCount() +
                ", aggregated=" + aggregatedCount() +
                ", reduction=" + String.format("%.1f%%", reductionPercentage()) +
                ", prefixes=" + toCidrStrings() +
                '}';
    }
}
