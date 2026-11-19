package com.msp.bmhsearch;

import java.util.List;

/**
 * Fast Boyer-Moore-Horspool search utilities for text CharSequence and raw byte arrays.
 */
public final class BoyerMooreHorspool {

    private BoyerMooreHorspool() {}

    /**
     * Finds index of first occurrence of pattern in text starting from 0.
     */
    public static int indexOf(CharSequence text, String pattern) {
        return indexOf(text, pattern, 0);
    }

    /**
     * Finds index of first occurrence of pattern in text starting from specified index.
     */
    public static int indexOf(CharSequence text, String pattern, int fromIndex) {
        return BmhPattern.compile(pattern).indexOf(text, fromIndex);
    }

    /**
     * Finds index of first case-insensitive occurrence of pattern in text.
     */
    public static int indexOfIgnoreCase(CharSequence text, String pattern) {
        return indexOfIgnoreCase(text, pattern, 0);
    }

    /**
     * Finds index of first case-insensitive occurrence of pattern in text starting from specified index.
     */
    public static int indexOfIgnoreCase(CharSequence text, String pattern, int fromIndex) {
        return BmhPattern.compile(pattern, true).indexOf(text, fromIndex);
    }

    /**
     * Finds all start indices of pattern occurrences in text.
     */
    public static List<Integer> findAll(CharSequence text, String pattern) {
        return BmhPattern.compile(pattern).findAll(text);
    }

    /**
     * Finds all start indices of case-insensitive pattern occurrences in text.
     */
    public static List<Integer> findAllIgnoreCase(CharSequence text, String pattern) {
        return BmhPattern.compile(pattern, true).findAll(text);
    }

    /**
     * Finds index of first occurrence of byte pattern in data starting from 0.
     */
    public static int indexOf(byte[] data, byte[] pattern) {
        return indexOf(data, pattern, 0);
    }

    /**
     * Finds index of first occurrence of byte pattern in data starting from specified index.
     */
    public static int indexOf(byte[] data, byte[] pattern, int fromIndex) {
        return BmhBytePattern.compile(pattern).indexOf(data, fromIndex);
    }

    /**
     * Finds all start indices of byte pattern occurrences in data.
     */
    public static List<Integer> findAll(byte[] data, byte[] pattern) {
        return BmhBytePattern.compile(pattern).findAll(data);
    }
}
