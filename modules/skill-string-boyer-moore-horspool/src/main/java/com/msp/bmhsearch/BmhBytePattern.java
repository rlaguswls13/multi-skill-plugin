package com.msp.bmhsearch;

import java.util.*;

/**
 * Precompiled Boyer-Moore-Horspool search pattern for raw byte arrays.
 */
public final class BmhBytePattern {

    private final byte[] pattern;
    private final int[] shift;
    private final int patternLength;

    private BmhBytePattern(byte[] pattern) {
        Objects.requireNonNull(pattern, "Pattern cannot be null");
        this.pattern = pattern.clone();
        this.patternLength = pattern.length;
        this.shift = new int[256];
        Arrays.fill(this.shift, Math.max(1, patternLength));

        if (patternLength > 1) {
            for (int j = 0; j < patternLength - 1; j++) {
                this.shift[pattern[j] & 0xFF] = patternLength - 1 - j;
            }
        }
    }

    /**
     * Compiles a byte search pattern.
     */
    public static BmhBytePattern compile(byte[] pattern) {
        return new BmhBytePattern(pattern);
    }

    /**
     * Returns a copy of the pattern byte array.
     */
    public byte[] pattern() {
        return pattern.clone();
    }

    /**
     * Finds the index of the first occurrence of this pattern in the data array, starting at fromIndex.
     */
    public int indexOf(byte[] data, int fromIndex) {
        if (data == null) {
            return -1;
        }
        int dataLength = data.length;
        if (patternLength == 0) {
            return (fromIndex <= dataLength) ? Math.max(0, fromIndex) : -1;
        }
        if (fromIndex >= dataLength || patternLength > dataLength) {
            return -1;
        }

        int i = Math.max(0, fromIndex) + patternLength - 1;
        while (i < dataLength) {
            int k = patternLength - 1;
            while (k >= 0 && data[i - (patternLength - 1 - k)] == pattern[k]) {
                k--;
            }
            if (k < 0) {
                return i - patternLength + 1;
            }
            i += shift[data[i] & 0xFF];
        }
        return -1;
    }

    /**
     * Finds all start indices of occurrences of this pattern in the byte array.
     */
    public List<Integer> findAll(byte[] data) {
        if (data == null || patternLength == 0) {
            return List.of();
        }
        List<Integer> matches = new ArrayList<>();
        int fromIndex = 0;
        int found;
        while ((found = indexOf(data, fromIndex)) != -1) {
            matches.add(found);
            fromIndex = found + 1;
        }
        return Collections.unmodifiableList(matches);
    }
}
