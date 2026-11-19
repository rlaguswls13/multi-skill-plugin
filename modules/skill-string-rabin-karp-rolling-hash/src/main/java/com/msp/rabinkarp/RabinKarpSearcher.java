package com.msp.rabinkarp;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Thread-safe Rabin-Karp exact substring searcher supporting single-pattern search
 * and multi-pattern equal-length batch search in O(N + M) expected time.
 */
public final class RabinKarpSearcher {

    private RabinKarpSearcher() {
    }

    /**
     * Finds all start indices where {@code pattern} occurs in {@code text} (including overlapping matches).
     */
    public static List<Integer> search(CharSequence text, CharSequence pattern) {
        Objects.requireNonNull(text, "text must not be null");
        Objects.requireNonNull(pattern, "pattern must not be null");
        int m = pattern.length();
        if (m == 0) {
            throw new IllegalArgumentException("pattern must not be empty");
        }
        int n = text.length();
        if (m > n) {
            return List.of();
        }

        DoubleHash target = RollingHash.compute(pattern, 0, m);
        RollingHash rolling = new RollingHash(m);
        DoubleHash current = rolling.init(text, 0);

        List<Integer> matches = new ArrayList<>();
        if (current.equals(target) && regionEquals(text, 0, pattern)) {
            matches.add(0);
        }
        for (int i = 1; i <= n - m; i++) {
            current = rolling.roll(text.charAt(i - 1), text.charAt(i + m - 1));
            if (current.equals(target) && regionEquals(text, i, pattern)) {
                matches.add(i);
            }
        }
        return matches;
    }

    /**
     * Finds the first occurrence index of {@code pattern} in {@code text}, or {@code -1} if absent.
     */
    public static int searchFirst(CharSequence text, CharSequence pattern) {
        Objects.requireNonNull(text, "text must not be null");
        Objects.requireNonNull(pattern, "pattern must not be null");
        int m = pattern.length();
        if (m == 0) {
            throw new IllegalArgumentException("pattern must not be empty");
        }
        int n = text.length();
        if (m > n) {
            return -1;
        }

        DoubleHash target = RollingHash.compute(pattern, 0, m);
        RollingHash rolling = new RollingHash(m);
        DoubleHash current = rolling.init(text, 0);
        if (current.equals(target) && regionEquals(text, 0, pattern)) {
            return 0;
        }
        for (int i = 1; i <= n - m; i++) {
            current = rolling.roll(text.charAt(i - 1), text.charAt(i + m - 1));
            if (current.equals(target) && regionEquals(text, i, pattern)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Searches {@code text} simultaneously for a batch of equal-length patterns in a single pass.
     *
     * @return map from each distinct pattern to its list of match start indices (empty list if not found)
     */
    public static Map<String, List<Integer>> searchMulti(CharSequence text, Collection<String> equalLengthPatterns) {
        Objects.requireNonNull(text, "text must not be null");
        Objects.requireNonNull(equalLengthPatterns, "equalLengthPatterns must not be null");
        if (equalLengthPatterns.isEmpty()) {
            return Map.of();
        }

        int m = -1;
        Map<String, List<Integer>> results = new LinkedHashMap<>();
        Map<DoubleHash, List<String>> hashBuckets = new HashMap<>();

        for (String pattern : equalLengthPatterns) {
            Objects.requireNonNull(pattern, "pattern element must not be null");
            if (pattern.isEmpty()) {
                throw new IllegalArgumentException("patterns must not be empty");
            }
            if (m == -1) {
                m = pattern.length();
            } else if (pattern.length() != m) {
                throw new IllegalArgumentException(
                        "All patterns in searchMulti must have equal length (expected " + m + ", got " + pattern.length() + ")");
            }
            if (!results.containsKey(pattern)) {
                results.put(pattern, new ArrayList<>());
                DoubleHash h = RollingHash.compute(pattern, 0, m);
                hashBuckets.computeIfAbsent(h, k -> new ArrayList<>()).add(pattern);
            }
        }

        int n = text.length();
        if (m > n) {
            return results;
        }

        RollingHash rolling = new RollingHash(m);
        DoubleHash current = rolling.init(text, 0);
        recordMatches(text, 0, current, hashBuckets, results);

        for (int i = 1; i <= n - m; i++) {
            current = rolling.roll(text.charAt(i - 1), text.charAt(i + m - 1));
            recordMatches(text, i, current, hashBuckets, results);
        }
        return results;
    }

    private static void recordMatches(
            CharSequence text,
            int pos,
            DoubleHash current,
            Map<DoubleHash, List<String>> hashBuckets,
            Map<String, List<Integer>> results
    ) {
        List<String> candidates = hashBuckets.get(current);
        if (candidates == null) {
            return;
        }
        for (String candidate : candidates) {
            if (regionEquals(text, pos, candidate)) {
                results.get(candidate).add(pos);
            }
        }
    }

    private static boolean regionEquals(CharSequence text, int start, CharSequence pattern) {
        int len = pattern.length();
        for (int i = 0; i < len; i++) {
            if (text.charAt(start + i) != pattern.charAt(i)) {
                return false;
            }
        }
        return true;
    }
}
