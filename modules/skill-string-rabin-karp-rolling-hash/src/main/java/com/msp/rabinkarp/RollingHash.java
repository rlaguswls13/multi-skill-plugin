package com.msp.rabinkarp;

import java.util.Objects;

/**
 * Polynomial Rolling Hash using two independent prime bases and moduli for
 * near-zero collision probability across both UTF-16 characters and raw byte streams.
 */
public final class RollingHash {

    public static final long MOD1 = 1_000_000_007L;
    public static final long BASE1 = 131_071L;

    public static final long MOD2 = 1_000_000_009L;
    public static final long BASE2 = 131_101L;

    private final int windowSize;
    private final long pow1;
    private final long pow2;

    private long h1;
    private long h2;

    public RollingHash(int windowSize) {
        if (windowSize <= 0) {
            throw new IllegalArgumentException("windowSize must be > 0, got: " + windowSize);
        }
        this.windowSize = windowSize;
        this.pow1 = modPow(BASE1, windowSize - 1, MOD1);
        this.pow2 = modPow(BASE2, windowSize - 1, MOD2);
    }

    public int windowSize() {
        return windowSize;
    }

    public DoubleHash currentHash() {
        return new DoubleHash(h1, h2);
    }

    /**
     * Initializes the rolling state from {@code text[start .. start + windowSize)}.
     */
    public DoubleHash init(CharSequence text, int start) {
        Objects.requireNonNull(text, "text must not be null");
        if (start < 0 || start + windowSize > text.length()) {
            throw new IndexOutOfBoundsException("Window exceeds text bounds");
        }
        long acc1 = 0;
        long acc2 = 0;
        for (int i = 0; i < windowSize; i++) {
            long sym = (text.charAt(start + i) & 0xFFFFL) + 1L;
            acc1 = (acc1 * BASE1 + sym) % MOD1;
            acc2 = (acc2 * BASE2 + sym) % MOD2;
        }
        this.h1 = acc1;
        this.h2 = acc2;
        return currentHash();
    }

    /**
     * Initializes the rolling state from {@code data[start .. start + windowSize)}.
     */
    public DoubleHash init(byte[] data, int start) {
        Objects.requireNonNull(data, "data must not be null");
        if (start < 0 || start + windowSize > data.length) {
            throw new IndexOutOfBoundsException("Window exceeds byte array bounds");
        }
        long acc1 = 0;
        long acc2 = 0;
        for (int i = 0; i < windowSize; i++) {
            long sym = (data[start + i] & 0xFFL) + 1L;
            acc1 = (acc1 * BASE1 + sym) % MOD1;
            acc2 = (acc2 * BASE2 + sym) % MOD2;
        }
        this.h1 = acc1;
        this.h2 = acc2;
        return currentHash();
    }

    /**
     * Advances the window by one symbol in O(1) time.
     *
     * @param outgoing unsigned symbol leaving the left edge of the window
     * @param incoming unsigned symbol entering the right edge of the window
     * @return updated {@link DoubleHash}
     */
    public DoubleHash roll(int outgoing, int incoming) {
        long outSym = (outgoing & 0xFFFFL) + 1L;
        long inSym = (incoming & 0xFFFFL) + 1L;

        long rem1 = (h1 - (outSym * pow1) % MOD1 + MOD1) % MOD1;
        h1 = (rem1 * BASE1 + inSym) % MOD1;

        long rem2 = (h2 - (outSym * pow2) % MOD2 + MOD2) % MOD2;
        h2 = (rem2 * BASE2 + inSym) % MOD2;

        return currentHash();
    }

    public static DoubleHash compute(CharSequence text, int start, int length) {
        Objects.requireNonNull(text, "text must not be null");
        if (length <= 0) {
            throw new IllegalArgumentException("length must be > 0");
        }
        RollingHash rh = new RollingHash(length);
        return rh.init(text, start);
    }

    public static DoubleHash compute(byte[] data, int start, int length) {
        Objects.requireNonNull(data, "data must not be null");
        if (length <= 0) {
            throw new IllegalArgumentException("length must be > 0");
        }
        RollingHash rh = new RollingHash(length);
        return rh.init(data, start);
    }

    private static long modPow(long base, int exp, long mod) {
        long result = 1L;
        long b = base % mod;
        int e = exp;
        while (e > 0) {
            if ((e & 1) == 1) {
                result = (result * b) % mod;
            }
            b = (b * b) % mod;
            e >>>= 1;
        }
        return result;
    }
}
