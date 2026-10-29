package com.msp.bloom;

import java.nio.charset.StandardCharsets;

/**
 * Built-in funnels for standard data types.
 */
public final class Funnels {

    private Funnels() {
    }

    /**
     * Funnel for UTF-8 String data.
     */
    public static Funnel<String> stringFunnel() {
        return (from, into) -> {
            if (from != null) {
                into.putString(from);
            }
        };
    }

    /**
     * Funnel for Long primitives.
     */
    public static Funnel<Long> longFunnel() {
        return (from, into) -> {
            if (from != null) {
                into.putLong(from);
            }
        };
    }

    /**
     * Funnel for Integer primitives.
     */
    public static Funnel<Integer> integerFunnel() {
        return (from, into) -> {
            if (from != null) {
                into.putInt(from);
            }
        };
    }

    /**
     * Funnel for raw byte arrays.
     */
    public static Funnel<byte[]> byteArrayFunnel() {
        return (from, into) -> {
            if (from != null) {
                into.putBytes(from);
            }
        };
    }
}
