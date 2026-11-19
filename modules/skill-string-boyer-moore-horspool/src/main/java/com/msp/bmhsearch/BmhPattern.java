package com.msp.bmhsearch;

import java.util.*;

/**
 * Precompiled Boyer-Moore-Horspool search pattern for CharSequence / String.
 */
public final class BmhPattern {

    private final String pattern;
    private final boolean ignoreCase;
    private final int[] asciiShift;
    private final Map<Character, Integer> unicodeShift;
    private final int patternLength;

    private BmhPattern(String pattern, boolean ignoreCase) {
        Objects.requireNonNull(pattern, "Pattern cannot be null");
        this.pattern = pattern;
        this.ignoreCase = ignoreCase;
        this.patternLength = pattern.length();
        this.asciiShift = new int[256];
        Arrays.fill(this.asciiShift, Math.max(1, patternLength));
        this.unicodeShift = new HashMap<>();

        if (patternLength > 1) {
            for (int j = 0; j < patternLength - 1; j++) {
                char c = pattern.charAt(j);
                int shift = patternLength - 1 - j;
                if (ignoreCase) {
                    setShift(Character.toLowerCase(c), shift);
                    setShift(Character.toUpperCase(c), shift);
                } else {
                    setShift(c, shift);
                }
            }
        }
    }

    private void setShift(char c, int shift) {
        if (c < 256) {
            asciiShift[c] = shift;
        } else {
            unicodeShift.put(c, shift);
        }
    }

    private int getShift(char c) {
        if (c < 256) {
            return asciiShift[c];
        }
        return unicodeShift.getOrDefault(c, patternLength);
    }

    /**
     * Compiles a case-sensitive search pattern.
     */
    public static BmhPattern compile(String pattern) {
        return new BmhPattern(pattern, false);
    }

    /**
     * Compiles a search pattern with optional case-insensitivity.
     */
    public static BmhPattern compile(String pattern, boolean ignoreCase) {
        return new BmhPattern(pattern, ignoreCase);
    }

    /**
     * Returns the compiled pattern string.
     */
    public String pattern() {
        return pattern;
    }

    /**
     * Returns true if case-insensitive.
     */
    public boolean isIgnoreCase() {
        return ignoreCase;
    }

    /**
     * Finds the index of the first occurrence of this pattern in the text, starting at fromIndex.
     *
     * @param text text to search in
     * @param fromIndex starting index
     * @return index of first match, or -1 if not found
     */
    public int indexOf(CharSequence text, int fromIndex) {
        if (text == null) {
            return -1;
        }
        int textLength = text.length();
        if (patternLength == 0) {
            return (fromIndex <= textLength) ? Math.max(0, fromIndex) : -1;
        }
        if (fromIndex >= textLength || patternLength > textLength) {
            return -1;
        }

        int i = Math.max(0, fromIndex) + patternLength - 1;
        while (i < textLength) {
            int k = patternLength - 1;
            while (k >= 0 && charMatches(text.charAt(i - (patternLength - 1 - k)), pattern.charAt(k))) {
                k--;
            }
            if (k < 0) {
                return i - patternLength + 1;
            }
            i += getShift(text.charAt(i));
        }
        return -1;
    }

    /**
     * Finds all start indices of occurrences of this pattern in the text.
     */
    public List<Integer> findAll(CharSequence text) {
        if (text == null || patternLength == 0) {
            return List.of();
        }
        List<Integer> matches = new ArrayList<>();
        int fromIndex = 0;
        int found;
        while ((found = indexOf(text, fromIndex)) != -1) {
            matches.add(found);
            fromIndex = found + 1;
        }
        return Collections.unmodifiableList(matches);
    }

    private boolean charMatches(char a, char b) {
        if (a == b) return true;
        if (!ignoreCase) return false;
        return Character.toLowerCase(a) == Character.toLowerCase(b)
            || Character.toUpperCase(a) == Character.toUpperCase(b);
    }
}
