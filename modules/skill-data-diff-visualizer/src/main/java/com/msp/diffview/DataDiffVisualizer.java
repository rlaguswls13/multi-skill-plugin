package com.msp.diffview;

import java.util.*;

/**
 * Visualizer and comparator for nested or flat Maps and key-value state objects.
 */
public final class DataDiffVisualizer {

    private DataDiffVisualizer() {}

    /**
     * Compares two Maps (supporting nested Maps) and produces a comprehensive DiffReport.
     */
    public static DiffReport diff(Map<String, ?> before, Map<String, ?> after) {
        Map<String, ?> b = before != null ? before : Collections.emptyMap();
        Map<String, ?> a = after != null ? after : Collections.emptyMap();

        List<DiffEntry> entries = new ArrayList<>();
        compareMaps("", b, a, entries);

        // Sort entries by path
        entries.sort(Comparator.comparing(DiffEntry::path));
        return new DiffReport(entries);
    }

    @SuppressWarnings("unchecked")
    private static void compareMaps(String prefix, Map<String, ?> before, Map<String, ?> after, List<DiffEntry> results) {
        Set<String> allKeys = new TreeSet<>();
        allKeys.addAll(before.keySet());
        allKeys.addAll(after.keySet());

        for (String key : allKeys) {
            String fullPath = prefix.isEmpty() ? key : prefix + "." + key;
            boolean inBefore = before.containsKey(key);
            boolean inAfter = after.containsKey(key);

            Object oldVal = before.get(key);
            Object newVal = after.get(key);

            if (inBefore && !inAfter) {
                results.add(new DiffEntry(fullPath, DiffType.REMOVED, oldVal, null));
            } else if (!inBefore && inAfter) {
                results.add(new DiffEntry(fullPath, DiffType.ADDED, null, newVal));
            } else {
                // Both contain key
                if (oldVal instanceof Map<?, ?> oldMap && newVal instanceof Map<?, ?> newMap) {
                    compareMaps(fullPath, (Map<String, ?>) oldMap, (Map<String, ?>) newMap, results);
                } else if (!Objects.equals(oldVal, newVal)) {
                    results.add(new DiffEntry(fullPath, DiffType.MODIFIED, oldVal, newVal));
                } else {
                    results.add(new DiffEntry(fullPath, DiffType.UNCHANGED, oldVal, newVal));
                }
            }
        }
    }
}
