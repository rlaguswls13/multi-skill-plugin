package com.msp.hangul;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Intelligent Korean Hangul initial consonant (초성) and mixed keyword search engine.
 * Supports:
 * - Pure initial consonant query: e.g. "ㅎㄱㄷ" matches "홍길동"
 * - Substring matching: e.g. "ㄱㄷ" matches "홍길동전"
 * - Mixed query: e.g. "홍ㄱㄷ" matches "홍길동"
 * - Alphanumeric & Hanja passthrough with case-insensitivity: e.g. "javaㄱㅂ" matches "Java개발자"
 * - Autocomplete in-flight syllable prefix matching: e.g. "홍기" matches "홍길동"
 * - Non-destructive HTML/terminal search highlight
 */
public final class ChoseongSearcher {

    private ChoseongSearcher() {
    }

    /**
     * Checks if the text matches the query anywhere as a substring.
     *
     * @param text  the target text to search within
     * @param query the query containing full syllables, choseong, or alphanumeric characters
     * @return true if a match is found; false otherwise
     */
    public static boolean matches(String text, String query) {
        if (text == null || query == null || text.isEmpty() || query.isEmpty()) {
            return false;
        }
        return !search(text, query).isEmpty();
    }

    /**
     * Searches the text for all occurrences matching the query, returning their ranges.
     *
     * @param text  the target text
     * @param query search query
     * @return list of non-overlapping matched ranges
     */
    public static List<MatchRange> search(String text, String query) {
        Objects.requireNonNull(text, "text must not be null");
        Objects.requireNonNull(query, "query must not be null");

        if (text.isEmpty() || query.isEmpty() || query.length() > text.length()) {
            return List.of();
        }

        List<MatchRange> results = new ArrayList<>();
        int textLen = text.length();
        int queryLen = query.length();

        int i = 0;
        while (i <= textLen - queryLen) {
            if (isMatchAt(text, query, i)) {
                results.add(new MatchRange(i, i + queryLen, text.substring(i, i + queryLen)));
                i += queryLen; // Advance past this match to avoid overlap
            } else {
                i++;
            }
        }

        return Collections.unmodifiableList(results);
    }

    /**
     * Highlights matched query occurrences in the text using enclosing tags.
     *
     * @param text    target text
     * @param query   search query
     * @param preTag  prefix highlight tag (e.g. {@code "<mark>"})
     * @param postTag postfix highlight tag (e.g. {@code "</mark>"})
     * @return highlighted text string
     */
    public static String highlight(String text, String query, String preTag, String postTag) {
        Objects.requireNonNull(text, "text must not be null");
        Objects.requireNonNull(query, "query must not be null");
        Objects.requireNonNull(preTag, "preTag must not be null");
        Objects.requireNonNull(postTag, "postTag must not be null");

        List<MatchRange> matches = search(text, query);
        if (matches.isEmpty()) {
            return text;
        }

        StringBuilder sb = new StringBuilder(text.length() + matches.size() * (preTag.length() + postTag.length()));
        int cursor = 0;

        for (MatchRange range : matches) {
            sb.append(text, cursor, range.start());
            sb.append(preTag);
            sb.append(text, range.start(), range.end());
            sb.append(postTag);
            cursor = range.end();
        }

        if (cursor < text.length()) {
            sb.append(text.substring(cursor));
        }

        return sb.toString();
    }

    private static boolean isMatchAt(String text, String query, int startIndex) {
        int queryLen = query.length();

        for (int j = 0; j < queryLen; j++) {
            char qChar = query.charAt(j);
            char tChar = text.charAt(startIndex + j);

            if (HangulDecomposer.isChoseong(qChar)) {
                // Query char is a Korean Choseong (initial consonant)
                if (HangulDecomposer.isHangulSyllable(tChar)) {
                    HangulSyllable tSyllable = HangulDecomposer.decompose(tChar);
                    if (qChar != tSyllable.choseong()) {
                        return false;
                    }
                } else if (qChar != tChar) {
                    return false;
                }
            } else if (HangulDecomposer.isHangulSyllable(qChar)) {
                // Query char is a composite Hangul syllable
                if (qChar == tChar) {
                    continue;
                }

                // If user is currently typing the last character without a final consonant,
                // allow matching syllables that share the same Cho + Jung
                boolean isLastChar = (j == queryLen - 1);
                HangulSyllable qSyllable = HangulDecomposer.decompose(qChar);
                if (isLastChar && !qSyllable.hasJongseong() && HangulDecomposer.isHangulSyllable(tChar)) {
                    HangulSyllable tSyllable = HangulDecomposer.decompose(tChar);
                    if (qSyllable.choseong() == tSyllable.choseong() && qSyllable.jungseong() == tSyllable.jungseong()) {
                        continue;
                    }
                }

                return false;
            } else {
                // Non-Hangul character (English, digit, punctuation, Hanja)
                if (Character.toLowerCase(qChar) != Character.toLowerCase(tChar)) {
                    return false;
                }
            }
        }

        return true;
    }
}
