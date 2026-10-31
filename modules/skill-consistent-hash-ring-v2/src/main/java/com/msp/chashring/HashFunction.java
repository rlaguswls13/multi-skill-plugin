package com.msp.chashring;

/**
 * Functional interface for calculating hash values for consistent hash ring keys.
 */
@FunctionalInterface
public interface HashFunction {
    /**
     * Computes a 64-bit hash for the given key string.
     *
     * @param key the input string
     * @return 64-bit integer hash value
     */
    long hash(String key);
}
