package com.msp.suffixarray;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable, thread-safe Suffix Array and LCP (Longest Common Prefix) engine.
 *
 * <p>Algorithms and complexities (where {@code N} is text length and {@code M} is pattern length):
 * <ul>
 *   <li>Suffix Array construction: {@code O(N log N)} via radix-sorted prefix doubling</li>
 *   <li>LCP Array construction: {@code O(N)} via Kasai's algorithm</li>
 *   <li>Pattern occurrence counting / listing: {@code O(M log N)} via dual binary search</li>
 *   <li>Distinct substring counting: {@code O(N)} via {@code N*(N+1)/2 - sum(LCP)}</li>
 *   <li>Longest repeated substring: {@code O(N)} scan over LCP array</li>
 *   <li>Longest common substring of two strings: {@code O((N+M) log(N+M))} generalized suffix array</li>
 * </ul>
 */
public final class SuffixArray {

    private final String text;
    private final int[] sa;
    private final int[] rank;
    private final int[] lcp;

    private SuffixArray(String text, int[] sa, int[] rank, int[] lcp) {
        this.text = text;
        this.sa = sa;
        this.rank = rank;
        this.lcp = lcp;
    }

    /**
     * Builds a {@link SuffixArray} and Kasai LCP array for {@code text}.
     */
    public static SuffixArray build(String text) {
        Objects.requireNonNull(text, "text must not be null");
        int n = text.length();
        if (n == 0) {
            return new SuffixArray(text, new int[0], new int[0], new int[0]);
        }
        int[] symbols = new int[n];
        for (int i = 0; i < n; i++) {
            symbols[i] = text.charAt(i);
        }
        int[] sa = buildSuffixArray(symbols);
        int[] invRank = buildInverseRank(sa);
        int[] lcp = buildLcpKasai(symbols, sa, invRank);
        return new SuffixArray(text, sa, invRank, lcp);
    }

    /**
     * Finds a longest common substring shared by {@code first} and {@code second} using a
     * generalized suffix array with a collision-free negative sentinel separator.
     *
     * @return a longest common substring, or {@code ""} if none exists
     */
    public static String longestCommonSubstring(String first, String second) {
        Objects.requireNonNull(first, "first must not be null");
        Objects.requireNonNull(second, "second must not be null");
        int n1 = first.length();
        int n2 = second.length();
        if (n1 == 0 || n2 == 0) {
            return "";
        }

        int total = n1 + 1 + n2;
        int[] combined = new int[total];
        for (int i = 0; i < n1; i++) {
            combined[i] = first.charAt(i);
        }
        combined[n1] = -1; // Guaranteed distinct from all Java char values [0..65535]
        for (int i = 0; i < n2; i++) {
            combined[n1 + 1 + i] = second.charAt(i);
        }

        int[] sa = buildSuffixArray(combined);
        int[] invRank = buildInverseRank(sa);
        int[] lcp = buildLcpKasai(combined, sa, invRank);

        int bestLen = 0;
        int bestPos = 0;
        for (int i = 1; i < total; i++) {
            int p1 = sa[i - 1];
            int p2 = sa[i];
            boolean p1InFirst = p1 < n1;
            boolean p2InFirst = p2 < n1;
            if (p1 != n1 && p2 != n1 && p1InFirst != p2InFirst) {
                if (lcp[i] > bestLen) {
                    bestLen = lcp[i];
                    bestPos = p1InFirst ? p1 : p2;
                }
            }
        }
        return bestLen == 0 ? "" : first.substring(bestPos, bestPos + bestLen);
    }

    public String text() {
        return text;
    }

    public int length() {
        return text.length();
    }

    /**
     * Returns a defensive copy of the suffix array {@code sa}, where {@code sa[i]} is the
     * starting index in {@link #text()} of the {@code i}-th lexicographically smallest suffix.
     */
    public int[] suffixArray() {
        return sa.clone();
    }

    /**
     * Returns a defensive copy of the inverse suffix array (0-based rank of suffix starting at {@code i}).
     */
    public int[] rankArray() {
        return rank.clone();
    }

    /**
     * Returns a defensive copy of the LCP array, where {@code lcp[0] = 0} and
     * {@code lcp[i] = LCP(suffix[sa[i-1]], suffix[sa[i]])} for {@code i >= 1}.
     */
    public int[] lcpArray() {
        return lcp.clone();
    }

    /**
     * Returns {@code true} if {@code pattern} occurs at least once in {@link #text()}.
     * Returns {@code false} for an empty pattern.
     */
    public boolean contains(String pattern) {
        return countOccurrences(pattern) > 0;
    }

    /**
     * Counts the number of occurrences of {@code pattern} in {@code O(M log N)} time.
     * Returns {@code 0} if {@code pattern} is empty.
     */
    public int countOccurrences(String pattern) {
        Objects.requireNonNull(pattern, "pattern must not be null");
        if (pattern.isEmpty() || sa.length == 0 || pattern.length() > text.length()) {
            return 0;
        }
        int lo = lowerBound(pattern);
        int hi = upperBound(pattern);
        return Math.max(0, hi - lo);
    }

    /**
     * Finds all starting indices where {@code pattern} occurs, sorted in ascending index order.
     * Returns an empty list if {@code pattern} is empty or not found.
     */
    public List<Integer> findOccurrences(String pattern) {
        Objects.requireNonNull(pattern, "pattern must not be null");
        if (pattern.isEmpty() || sa.length == 0 || pattern.length() > text.length()) {
            return List.of();
        }
        int lo = lowerBound(pattern);
        int hi = upperBound(pattern);
        if (hi <= lo) {
            return List.of();
        }
        int[] positions = new int[hi - lo];
        for (int i = lo; i < hi; i++) {
            positions[i - lo] = sa[i];
        }
        Arrays.sort(positions);
        List<Integer> result = new ArrayList<>(positions.length);
        for (int pos : positions) {
            result.add(pos);
        }
        return List.copyOf(result);
    }

    /**
     * Returns the lexicographically smallest longest repeated substring (occurring at least twice),
     * or {@code ""} if all characters are distinct.
     */
    public String longestRepeatedSubstring() {
        int maxLcp = 0;
        int bestIdx = -1;
        for (int i = 1; i < lcp.length; i++) {
            if (lcp[i] > maxLcp) {
                maxLcp = lcp[i];
                bestIdx = i;
            }
        }
        if (maxLcp == 0) {
            return "";
        }
        int start = sa[bestIdx];
        return text.substring(start, start + maxLcp);
    }

    /**
     * Returns all distinct substrings tied for the maximum repeated length, in lexicographical order.
     */
    public List<String> longestRepeatedSubstrings() {
        int maxLcp = 0;
        for (int i = 1; i < lcp.length; i++) {
            if (lcp[i] > maxLcp) {
                maxLcp = lcp[i];
            }
        }
        if (maxLcp == 0) {
            return List.of();
        }
        Set<String> results = new LinkedHashSet<>();
        for (int i = 1; i < lcp.length; i++) {
            if (lcp[i] == maxLcp) {
                int start = sa[i];
                results.add(text.substring(start, start + maxLcp));
            }
        }
        return List.copyOf(results);
    }

    /**
     * Counts the total number of distinct non-empty substrings of {@link #text()} in {@code O(N)} time.
     */
    public long countDistinctSubstrings() {
        long n = text.length();
        long total = n * (n + 1) / 2;
        long lcpSum = 0;
        for (int v : lcp) {
            lcpSum += v;
        }
        return total - lcpSum;
    }

    private int lowerBound(String pattern) {
        int lo = 0;
        int hi = sa.length;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (compareSuffixPrefix(sa[mid], pattern) < 0) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }

    private int upperBound(String pattern) {
        int lo = 0;
        int hi = sa.length;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (compareSuffixPrefix(sa[mid], pattern) <= 0) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }

    private int compareSuffixPrefix(int suffixStart, String pattern) {
        int n = text.length();
        int m = pattern.length();
        int limit = Math.min(n - suffixStart, m);
        for (int i = 0; i < limit; i++) {
            char a = text.charAt(suffixStart + i);
            char b = pattern.charAt(i);
            if (a != b) {
                return a - b;
            }
        }
        // If suffix ran out of characters before matching full pattern, suffix < pattern
        return (n - suffixStart < m) ? -1 : 0;
    }

    /**
     * O(N log N) prefix-doubling suffix array builder with radix sort.
     */
    private static int[] buildSuffixArray(int[] symbols) {
        int n = symbols.length;
        if (n == 0) {
            return new int[0];
        }
        if (n == 1) {
            return new int[]{0};
        }

        int minSym = symbols[0];
        int maxSym = symbols[0];
        for (int s : symbols) {
            if (s < minSym) minSym = s;
            if (s > maxSym) maxSym = s;
        }

        int range = maxSym - minSym + 1;
        int[] sa = new int[n];
        int[] cnt = new int[Math.max(n + 1, range)];

        // Initial counting sort on single characters
        for (int s : symbols) {
            cnt[s - minSym]++;
        }
        for (int i = 1; i < range; i++) {
            cnt[i] += cnt[i - 1];
        }
        for (int i = n - 1; i >= 0; i--) {
            sa[--cnt[symbols[i] - minSym]] = i;
        }

        int[] rank = new int[n];
        int[] newRank = new int[n];
        int[] tmp = new int[n];

        rank[sa[0]] = 1;
        int classes = 1;
        for (int i = 1; i < n; i++) {
            if (symbols[sa[i]] != symbols[sa[i - 1]]) {
                classes++;
            }
            rank[sa[i]] = classes;
        }

        for (int len = 1; len < n && classes < n; len <<= 1) {
            // Order suffixes by second half: suffixes with index + len >= n have rank 0 (smallest)
            int p = 0;
            for (int i = n - len; i < n; i++) {
                tmp[p++] = i;
            }
            for (int i = 0; i < n; i++) {
                if (sa[i] >= len) {
                    tmp[p++] = sa[i] - len;
                }
            }

            // Stable counting sort by first half rank (1..classes)
            Arrays.fill(cnt, 0, classes + 1, 0);
            for (int i = 0; i < n; i++) {
                cnt[rank[tmp[i]]]++;
            }
            for (int i = 1; i <= classes; i++) {
                cnt[i] += cnt[i - 1];
            }
            for (int i = n - 1; i >= 0; i--) {
                int idx = tmp[i];
                sa[--cnt[rank[idx]]] = idx;
            }

            // Recompute equivalence classes
            newRank[sa[0]] = 1;
            classes = 1;
            for (int i = 1; i < n; i++) {
                int cur = sa[i];
                int prev = sa[i - 1];
                int curSecond = (cur + len < n) ? rank[cur + len] : 0;
                int prevSecond = (prev + len < n) ? rank[prev + len] : 0;
                if (rank[cur] != rank[prev] || curSecond != prevSecond) {
                    classes++;
                }
                newRank[cur] = classes;
            }

            int[] swap = rank;
            rank = newRank;
            newRank = swap;
        }
        return sa;
    }

    private static int[] buildInverseRank(int[] sa) {
        int[] inv = new int[sa.length];
        for (int i = 0; i < sa.length; i++) {
            inv[sa[i]] = i;
        }
        return inv;
    }

    /**
     * O(N) Kasai algorithm for LCP array construction.
     */
    private static int[] buildLcpKasai(int[] symbols, int[] sa, int[] invRank) {
        int n = symbols.length;
        int[] lcp = new int[n];
        int k = 0;
        for (int i = 0; i < n; i++) {
            int r = invRank[i];
            if (r == 0) {
                k = 0;
                continue;
            }
            int prevSuffix = sa[r - 1];
            while (i + k < n && prevSuffix + k < n && symbols[i + k] == symbols[prevSuffix + k]) {
                k++;
            }
            lcp[r] = k;
            if (k > 0) {
                k--;
            }
        }
        return lcp;
    }
}
