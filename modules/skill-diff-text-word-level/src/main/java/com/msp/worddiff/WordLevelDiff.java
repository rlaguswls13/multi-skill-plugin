package com.msp.worddiff;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Word, whitespace, and punctuation level text diff engine generating
 * fine-grained edits and inline markup.
 */
public final class WordLevelDiff {

    // Matches whitespace chunks, Unicode punctuation/symbols, or word/alphanumeric sequences
    private static final Pattern TOKEN_PATTERN =
            Pattern.compile("\\s+|\\p{P}|\\p{S}|[^\\s\\p{P}\\p{S}]+");

    private WordLevelDiff() {
        // Utility class
    }

    /**
     * Splits text into fine-grained tokens: words, whitespace blocks, and punctuation marks.
     *
     * @param text input string
     * @return ordered list of tokens
     */
    public static List<String> tokenize(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        List<String> tokens = new ArrayList<>();
        Matcher matcher = TOKEN_PATTERN.matcher(text);
        while (matcher.find()) {
            tokens.add(matcher.group());
        }
        return Collections.unmodifiableList(tokens);
    }

    /**
     * Computes the word-level diff between original and revised text using
     * the Shortest Edit Script (SES) algorithm.
     *
     * @param original original text
     * @param revised  revised text
     * @return coalesced list of diff items
     */
    public static List<WordDiffItem> diffWords(String original, String revised) {
        Objects.requireNonNull(original, "original must not be null");
        Objects.requireNonNull(revised, "revised must not be null");

        if (original.equals(revised)) {
            if (original.isEmpty()) {
                return List.of();
            }
            return List.of(new WordDiffItem(WordDiffOp.EQUAL, original));
        }

        List<String> a = tokenize(original);
        List<String> b = tokenize(revised);

        if (a.isEmpty() && b.isEmpty()) {
            return List.of();
        }
        if (a.isEmpty()) {
            return List.of(new WordDiffItem(WordDiffOp.INSERT, revised));
        }
        if (b.isEmpty()) {
            return List.of(new WordDiffItem(WordDiffOp.DELETE, original));
        }

        int n = a.size();
        int m = b.size();

        // 1. Strip common prefix
        int prefix = 0;
        while (prefix < n && prefix < m && a.get(prefix).equals(b.get(prefix))) {
            prefix++;
        }

        // 2. Strip common suffix
        int suffix = 0;
        while (suffix < n - prefix && suffix < m - prefix &&
                a.get(n - 1 - suffix).equals(b.get(m - 1 - suffix))) {
            suffix++;
        }

        List<WordDiffItem> rawDiffs = new ArrayList<>();

        // Add common prefix
        for (int i = 0; i < prefix; i++) {
            rawDiffs.add(new WordDiffItem(WordDiffOp.EQUAL, a.get(i)));
        }

        // Sublist for Myers SES
        List<String> aSub = a.subList(prefix, n - suffix);
        List<String> bSub = b.subList(prefix, m - suffix);

        if (aSub.isEmpty() && !bSub.isEmpty()) {
            for (String tok : bSub) {
                rawDiffs.add(new WordDiffItem(WordDiffOp.INSERT, tok));
            }
        } else if (!aSub.isEmpty() && bSub.isEmpty()) {
            for (String tok : aSub) {
                rawDiffs.add(new WordDiffItem(WordDiffOp.DELETE, tok));
            }
        } else if (!aSub.isEmpty()) {
            rawDiffs.addAll(computeMyersSes(aSub, bSub));
        }

        // Add common suffix
        for (int i = n - suffix; i < n; i++) {
            rawDiffs.add(new WordDiffItem(WordDiffOp.EQUAL, a.get(i)));
        }

        return coalesce(rawDiffs);
    }

    /**
     * Renders inline diff using standard bracket markup:
     * {@code "[-deleted-]"} for deletions and {@code "{+inserted+}"} for insertions.
     *
     * @param original original text
     * @param revised  revised text
     * @return formatted inline diff string
     */
    public static String toInlineMarkup(String original, String revised) {
        return toCustomMarkup(original, revised, "[-", "-]", "{+", "+}");
    }

    /**
     * Renders inline diff with custom prefixes and suffixes for deletions and insertions.
     *
     * @param original  original text
     * @param revised   revised text
     * @param delPrefix deletion prefix (e.g., {@code "<del>"})
     * @param delSuffix deletion suffix (e.g., {@code "</del>"})
     * @param insPrefix insertion prefix (e.g., {@code "<ins>"})
     * @param insSuffix insertion suffix (e.g., {@code "</ins>"})
     * @return formatted custom markup string
     */
    public static String toCustomMarkup(String original, String revised,
                                        String delPrefix, String delSuffix,
                                        String insPrefix, String insSuffix) {
        Objects.requireNonNull(delPrefix, "delPrefix must not be null");
        Objects.requireNonNull(delSuffix, "delSuffix must not be null");
        Objects.requireNonNull(insPrefix, "insPrefix must not be null");
        Objects.requireNonNull(insSuffix, "insSuffix must not be null");

        List<WordDiffItem> items = diffWords(original, revised);
        StringBuilder sb = new StringBuilder();
        for (WordDiffItem item : items) {
            switch (item.op()) {
                case EQUAL -> sb.append(item.text());
                case DELETE -> sb.append(delPrefix).append(item.text()).append(delSuffix);
                case INSERT -> sb.append(insPrefix).append(item.text()).append(insSuffix);
            }
        }
        return sb.toString();
    }

    /**
     * Coalesces consecutive diff items having the same operation into single items.
     *
     * @param items raw diff items
     * @return coalesced diff items
     */
    public static List<WordDiffItem> coalesce(List<WordDiffItem> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }

        List<WordDiffItem> coalesced = new ArrayList<>();
        WordDiffOp currentOp = null;
        StringBuilder currentText = new StringBuilder();

        for (WordDiffItem item : items) {
            if (item.op() == currentOp) {
                currentText.append(item.text());
            } else {
                if (currentOp != null) {
                    coalesced.add(new WordDiffItem(currentOp, currentText.toString()));
                }
                currentOp = item.op();
                currentText.setLength(0);
                currentText.append(item.text());
            }
        }
        if (currentOp != null && currentText.length() > 0) {
            coalesced.add(new WordDiffItem(currentOp, currentText.toString()));
        }

        return Collections.unmodifiableList(coalesced);
    }

    private static List<WordDiffItem> computeMyersSes(List<String> a, List<String> b) {
        int n = a.size();
        int m = b.size();
        int max = n + m;
        int offset = max;

        int[] v = new int[2 * max + 1];
        v[offset + 1] = 0;
        List<int[]> history = new ArrayList<>();

        int finalD = -1;

        search:
        for (int d = 0; d <= max; d++) {
            int[] vNext = (d == 0) ? v.clone() : history.get(d - 1).clone();

            for (int k = -d; k <= d; k += 2) {
                int x;
                // Prefer DELETE (horizontal move) over INSERT (vertical move) when equal
                if (k == -d || (k != d && vNext[offset + k - 1] < vNext[offset + k + 1])) {
                    x = vNext[offset + k + 1]; // move down (insert)
                } else {
                    x = vNext[offset + k - 1] + 1; // move right (delete)
                }
                int y = x - k;

                while (x < n && y < m && a.get(x).equals(b.get(y))) {
                    x++;
                    y++;
                }
                vNext[offset + k] = x;

                if (x >= n && y >= m) {
                    history.add(vNext);
                    finalD = d;
                    break search;
                }
            }
            history.add(vNext);
        }

        if (finalD == -1) {
            // Fallback: full replacement
            List<WordDiffItem> fallback = new ArrayList<>();
            for (String tok : a) {
                fallback.add(new WordDiffItem(WordDiffOp.DELETE, tok));
            }
            for (String tok : b) {
                fallback.add(new WordDiffItem(WordDiffOp.INSERT, tok));
            }
            return fallback;
        }

        // Backtrack through history to recover edit script
        List<WordDiffItem> edits = new ArrayList<>();
        int currX = n;
        int currY = m;

        for (int d = finalD; d > 0; d--) {
            int k = currX - currY;
            int[] vPrev = history.get(d - 1);

            boolean down = (k == -d || (k != d && vPrev[offset + k - 1] < vPrev[offset + k + 1]));
            int prevK = down ? k + 1 : k - 1;
            int prevX = vPrev[offset + prevK];
            int prevY = prevX - prevK;

            int snakeStartX = down ? prevX : prevX + 1;
            int snakeStartY = down ? prevY + 1 : prevY;

            while (currX > snakeStartX && currY > snakeStartY) {
                edits.add(new WordDiffItem(WordDiffOp.EQUAL, a.get(currX - 1)));
                currX--;
                currY--;
            }

            if (down) {
                edits.add(new WordDiffItem(WordDiffOp.INSERT, b.get(prevY)));
            } else {
                edits.add(new WordDiffItem(WordDiffOp.DELETE, a.get(prevX)));
            }

            currX = prevX;
            currY = prevY;
        }

        while (currX > 0 && currY > 0) {
            edits.add(new WordDiffItem(WordDiffOp.EQUAL, a.get(currX - 1)));
            currX--;
            currY--;
        }

        Collections.reverse(edits);
        return edits;
    }
}
