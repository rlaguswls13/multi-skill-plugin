package com.msp.textdiff;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * High-performance, zero-dependency text difference engine based on
 * the Longest Common Subsequence (LCS) algorithm.
 * Supports line-level and token/word-level diff analysis.
 */
public final class TextDiffEngine {

    private static final Pattern WORD_TOKEN_PATTERN = Pattern.compile("\\s+|[\\p{L}\\p{N}_]+|[^\\s\\p{L}\\p{N}_]");

    private TextDiffEngine() {
        // Utility class
    }

    /**
     * Computes line-by-line diff between two multi-line strings.
     *
     * @param oldText Original string.
     * @param newText Modified string.
     * @return List of line differences with accurate line numbers.
     */
    public static List<LineDiff> diffLines(String oldText, String newText) {
        List<String> oldLines = splitLines(oldText);
        List<String> newLines = splitLines(newText);

        List<RawDiff<String>> rawDiffs = computeLcs(oldLines, newLines);
        List<LineDiff> lineDiffs = new ArrayList<>(rawDiffs.size());

        int oldLineNum = 1;
        int newLineNum = 1;

        for (RawDiff<String> item : rawDiffs) {
            switch (item.type()) {
                case EQUAL -> {
                    lineDiffs.add(new LineDiff(DiffType.EQUAL, item.oldItem(), oldLineNum++, newLineNum++));
                }
                case DELETE -> {
                    lineDiffs.add(new LineDiff(DiffType.DELETE, item.oldItem(), oldLineNum++, -1));
                }
                case INSERT -> {
                    lineDiffs.add(new LineDiff(DiffType.INSERT, item.newItem(), -1, newLineNum++));
                }
            }
        }

        return Collections.unmodifiableList(lineDiffs);
    }

    /**
     * Computes word and token-level diff between two strings.
     *
     * @param oldText Original string.
     * @param newText Modified string.
     * @return List of word/token differences.
     */
    public static List<WordDiff> diffWords(String oldText, String newText) {
        List<String> oldTokens = tokenizeWords(oldText);
        List<String> newTokens = tokenizeWords(newText);

        List<RawDiff<String>> rawDiffs = computeLcs(oldTokens, newTokens);
        List<WordDiff> wordDiffs = new ArrayList<>(rawDiffs.size());

        for (RawDiff<String> item : rawDiffs) {
            String text = (item.type() == DiffType.INSERT) ? item.newItem() : item.oldItem();
            wordDiffs.add(new WordDiff(item.type(), text));
        }

        return Collections.unmodifiableList(wordDiffs);
    }

    /**
     * Splits a string into lines preserving line structure.
     */
    public static List<String> splitLines(String text) {
        if (text == null || text.isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.asList(text.split("\\R", -1));
    }

    /**
     * Tokenizes a string into words, whitespace, and punctuation symbols.
     */
    public static List<String> tokenizeWords(String text) {
        if (text == null || text.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> tokens = new ArrayList<>();
        Matcher matcher = WORD_TOKEN_PATTERN.matcher(text);
        while (matcher.find()) {
            tokens.add(matcher.group());
        }
        return tokens;
    }

    private record RawDiff<T>(DiffType type, T oldItem, T newItem) {}

    private static <T> List<RawDiff<T>> computeLcs(List<T> a, List<T> b) {
        int m = a.size();
        int n = b.size();

        int[][] dp = new int[m + 1][n + 1];
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                if (Objects.equals(a.get(i), b.get(j))) {
                    dp[i + 1][j + 1] = dp[i][j] + 1;
                } else {
                    dp[i + 1][j + 1] = Math.max(dp[i + 1][j], dp[i][j + 1]);
                }
            }
        }

        List<RawDiff<T>> result = new ArrayList<>();
        int i = m;
        int j = n;
        while (i > 0 || j > 0) {
            if (i > 0 && j > 0 && Objects.equals(a.get(i - 1), b.get(j - 1))) {
                result.add(new RawDiff<>(DiffType.EQUAL, a.get(i - 1), b.get(j - 1)));
                i--;
                j--;
            } else if (j > 0 && (i == 0 || dp[i][j - 1] >= dp[i - 1][j])) {
                result.add(new RawDiff<>(DiffType.INSERT, null, b.get(j - 1)));
                j--;
            } else if (i > 0 && (j == 0 || dp[i][j - 1] < dp[i - 1][j])) {
                result.add(new RawDiff<>(DiffType.DELETE, a.get(i - 1), null));
                i--;
            }
        }
        Collections.reverse(result);
        return result;
    }
}
