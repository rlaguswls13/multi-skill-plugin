package com.msp.bgp;

import java.util.*;

/**
 * IP Route Summarization & CIDR Prefix Aggregator (BGP Route Aggregator).
 * Aggregates large sets of IPv4 and IPv6 routing prefixes into the minimum non-overlapping routing table.
 */
public final class BgpPrefixAggregator {

    private BgpPrefixAggregator() {}

    public static AggregationResult aggregate(Collection<String> cidrs) {
        if (cidrs == null || cidrs.isEmpty()) {
            return new AggregationResult(List.of(), List.of());
        }
        List<CidrPrefix> parsed = cidrs.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(CidrPrefix::parse)
                .toList();
        return aggregatePrefixes(parsed);
    }

    public static AggregationResult aggregate(String... cidrs) {
        if (cidrs == null || cidrs.length == 0) {
            return new AggregationResult(List.of(), List.of());
        }
        return aggregate(Arrays.asList(cidrs));
    }

    public static AggregationResult aggregatePrefixes(Collection<CidrPrefix> prefixes) {
        if (prefixes == null || prefixes.isEmpty()) {
            return new AggregationResult(List.of(), List.of());
        }

        List<CidrPrefix> originalList = List.copyOf(prefixes);

        List<CidrPrefix> v4List = new ArrayList<>();
        List<CidrPrefix> v6List = new ArrayList<>();

        for (CidrPrefix p : originalList) {
            if (p.getVersion() == IpVersion.IPV4) {
                v4List.add(p);
            } else {
                v6List.add(p);
            }
        }

        List<CidrPrefix> aggregatedV4 = aggregateVersion(v4List);
        List<CidrPrefix> aggregatedV6 = aggregateVersion(v6List);

        List<CidrPrefix> combined = new ArrayList<>(aggregatedV4.size() + aggregatedV6.size());
        combined.addAll(aggregatedV4);
        combined.addAll(aggregatedV6);
        Collections.sort(combined);

        return new AggregationResult(originalList, combined);
    }

    private static List<CidrPrefix> aggregateVersion(List<CidrPrefix> list) {
        if (list.isEmpty()) {
            return Collections.emptyList();
        }

        List<CidrPrefix> current = eliminateRedundancy(list);

        boolean changed;
        do {
            changed = false;
            // Group by prefix length
            Map<Integer, List<CidrPrefix>> byLen = new TreeMap<>(Collections.reverseOrder());
            for (CidrPrefix p : current) {
                byLen.computeIfAbsent(p.getPrefixLength(), k -> new ArrayList<>()).add(p);
            }

            List<CidrPrefix> nextList = new ArrayList<>();

            for (Map.Entry<Integer, List<CidrPrefix>> entry : byLen.entrySet()) {
                int len = entry.getKey();
                List<CidrPrefix> group = entry.getValue();

                if (len == 0) {
                    nextList.addAll(group);
                    continue;
                }

                Collections.sort(group);
                boolean[] merged = new boolean[group.size()];

                for (int i = 0; i < group.size(); i++) {
                    if (merged[i]) continue;
                    CidrPrefix p1 = group.get(i);

                    // Sibling would be sorted adjacent to p1
                    if (i + 1 < group.size() && !merged[i + 1]) {
                        CidrPrefix p2 = group.get(i + 1);
                        Optional<CidrPrefix> parent = p1.mergeWithSibling(p2);
                        if (parent.isPresent()) {
                            nextList.add(parent.get());
                            merged[i] = true;
                            merged[i + 1] = true;
                            changed = true;
                            continue;
                        }
                    }
                    nextList.add(p1);
                }
            }

            if (changed) {
                current = eliminateRedundancy(nextList);
            } else {
                current = nextList;
            }
        } while (changed);

        Collections.sort(current);
        return current;
    }

    /**
     * Eliminates duplicate and redundant (strictly contained) subnets.
     */
    public static List<CidrPrefix> eliminateRedundancy(List<CidrPrefix> prefixes) {
        if (prefixes == null || prefixes.isEmpty()) {
            return Collections.emptyList();
        }

        // Sort by networkAddress ascending, then prefixLength ascending (larger subnet first)
        List<CidrPrefix> sorted = new ArrayList<>(new HashSet<>(prefixes));
        Collections.sort(sorted);

        List<CidrPrefix> result = new ArrayList<>();
        for (CidrPrefix candidate : sorted) {
            boolean contained = false;
            for (CidrPrefix existing : result) {
                if (existing.contains(candidate)) {
                    contained = true;
                    break;
                }
            }
            if (!contained) {
                result.add(candidate);
            }
        }

        return result;
    }
}
