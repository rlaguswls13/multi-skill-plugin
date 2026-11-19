package com.msp.rabinkarp;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Document fingerprinting via the Schleimer-Wilkerson-Aiken Winnowing algorithm.
 * <p>
 * Guarantees that any shared substring of length {@code >= kGramSize + windowSize - 1}
 * between two documents produces at least one identical {@link Fingerprint#hash()}.
 */
public final class WinnowingFingerprinter {

    public record Fingerprint(DoubleHash hash, int position) {
    }

    private final int kGramSize;
    private final int windowSize;

    public WinnowingFingerprinter(int kGramSize, int windowSize) {
        if (kGramSize <= 0) {
            throw new IllegalArgumentException("kGramSize must be > 0");
        }
        if (windowSize <= 0) {
            throw new IllegalArgumentException("windowSize must be > 0");
        }
        this.kGramSize = kGramSize;
        this.windowSize = windowSize;
    }

    public int kGramSize() {
        return kGramSize;
    }

    public int windowSize() {
        return windowSize;
    }

    /**
     * Computes winnowed fingerprints for {@code text}.
     */
    public List<Fingerprint> fingerprint(CharSequence text) {
        Objects.requireNonNull(text, "text must not be null");
        int n = text.length();
        if (n < kGramSize) {
            return List.of();
        }

        int numKGrams = n - kGramSize + 1;
        DoubleHash[] hashes = new DoubleHash[numKGrams];
        RollingHash rolling = new RollingHash(kGramSize);
        hashes[0] = rolling.init(text, 0);
        for (int i = 1; i < numKGrams; i++) {
            hashes[i] = rolling.roll(text.charAt(i - 1), text.charAt(i + kGramSize - 1));
        }

        if (numKGrams <= windowSize) {
            int minIdx = 0;
            for (int i = 1; i < numKGrams; i++) {
                if (hashes[i].compareTo(hashes[minIdx]) <= 0) {
                    minIdx = i;
                }
            }
            return List.of(new Fingerprint(hashes[minIdx], minIdx));
        }

        List<Fingerprint> result = new ArrayList<>();
        int lastSelectedPos = -1;
        int windowCount = numKGrams - windowSize + 1;

        for (int wStart = 0; wStart < windowCount; wStart++) {
            int wEnd = wStart + windowSize;
            int minIdx = wStart;
            for (int j = wStart + 1; j < wEnd; j++) {
                // Rightmost tie-breaking as specified by the Winnowing algorithm
                if (hashes[j].compareTo(hashes[minIdx]) <= 0) {
                    minIdx = j;
                }
            }
            if (minIdx != lastSelectedPos) {
                result.add(new Fingerprint(hashes[minIdx], minIdx));
                lastSelectedPos = minIdx;
            }
        }
        return result;
    }
}
