package com.msp.simhash;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 64-bit SimHash Locality-Sensitive Hashing (LSH) engine for near-duplicate text detection.
 * SimHash produces fingerprints where similar texts have a small Hamming distance.
 */
public final class SimHash {

    private SimHash() {}

    /**
     * Computes the 64-bit SimHash fingerprint of a document using the default word tokenizer.
     *
     * @param document Input document string
     * @return 64-bit SimHash fingerprint
     */
    public static long compute(String document) {
        return compute(document, Tokenizer.WORDS);
    }

    /**
     * Computes the 64-bit SimHash fingerprint of a document using a custom tokenizer.
     *
     * @param document  Input document string
     * @param tokenizer Tokenization strategy
     * @return 64-bit SimHash fingerprint
     */
    public static long compute(String document, Tokenizer tokenizer) {
        Objects.requireNonNull(tokenizer, "tokenizer must not be null");
        if (document == null || document.isBlank()) {
            return 0L;
        }
        List<String> tokens = tokenizer.tokenize(document);
        return computeFromTokens(tokens);
    }

    /**
     * Computes the 64-bit SimHash fingerprint from a list of tokens.
     * Token occurrences are weighted by frequency.
     *
     * @param tokens List of token strings
     * @return 64-bit SimHash fingerprint
     */
    public static long computeFromTokens(List<String> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            return 0L;
        }
        Map<String, Integer> frequencies = new HashMap<>();
        for (String token : tokens) {
            frequencies.merge(token, 1, Integer::sum);
        }
        return computeFromWeightedTokens(frequencies);
    }

    /**
     * Computes the 64-bit SimHash fingerprint from weighted tokens.
     *
     * @param weightedTokens Map of token to integer weight (e.g. TF-IDF or frequency)
     * @return 64-bit SimHash fingerprint
     */
    public static long computeFromWeightedTokens(Map<String, Integer> weightedTokens) {
        if (weightedTokens == null || weightedTokens.isEmpty()) {
            return 0L;
        }

        int[] bitCounts = new int[64];

        for (Map.Entry<String, Integer> entry : weightedTokens.entrySet()) {
            String token = entry.getKey();
            int weight = entry.getValue();
            if (weight <= 0) {
                continue;
            }

            long tokenHash = hash64(token);

            for (int i = 0; i < 64; i++) {
                if (((tokenHash >>> i) & 1L) == 1L) {
                    bitCounts[i] += weight;
                } else {
                    bitCounts[i] -= weight;
                }
            }
        }

        long fingerprint = 0L;
        for (int i = 0; i < 64; i++) {
            if (bitCounts[i] > 0) {
                fingerprint |= (1L << i);
            }
        }
        return fingerprint;
    }

    /**
     * 64-bit hash with high bit avalanche mixing.
     */
    public static long hash64(String token) {
        if (token == null) {
            return 0L;
        }
        byte[] bytes = token.getBytes(StandardCharsets.UTF_8);
        long hash = 0xcbf29ce484222325L;
        for (byte b : bytes) {
            hash ^= (b & 0xff);
            hash *= 0x100000001b3L;
        }
        // 64-bit avalanche mixing
        hash ^= hash >>> 33;
        hash *= 0xff51afd7ed558ccdL;
        hash ^= hash >>> 33;
        hash *= 0xc4ceb9fe1a85ec53L;
        hash ^= hash >>> 33;
        return hash;
    }

    /**
     * Calculates the Hamming distance (number of differing bits) between two 64-bit hashes.
     *
     * @param hash1 First SimHash
     * @param hash2 Second SimHash
     * @return Hamming distance (0 to 64)
     */
    public static int hammingDistance(long hash1, long hash2) {
        return Long.bitCount(hash1 ^ hash2);
    }

    /**
     * Tests if two 64-bit hashes are near-duplicates within a maximum Hamming distance.
     *
     * @param hash1              First SimHash
     * @param hash2              Second SimHash
     * @param maxHammingDistance Maximum allowed Hamming distance (typically 3 for web documents)
     * @return true if distance <= maxHammingDistance
     */
    public static boolean isNearDuplicate(long hash1, long hash2, int maxHammingDistance) {
        return hammingDistance(hash1, hash2) <= maxHammingDistance;
    }

    /**
     * Tests if two documents are near-duplicates within a maximum Hamming distance.
     *
     * @param doc1               First document
     * @param doc2               Second document
     * @param maxHammingDistance Maximum allowed Hamming distance
     * @return true if distance <= maxHammingDistance
     */
    public static boolean isNearDuplicate(String doc1, String doc2, int maxHammingDistance) {
        return isNearDuplicate(compute(doc1), compute(doc2), maxHammingDistance);
    }

    /**
     * Computes normalized bit similarity ratio in range [0.0, 1.0].
     * 1.0 means identical 64-bit hash; 0.0 means completely inverted.
     */
    public static double similarity(long hash1, long hash2) {
        int dist = hammingDistance(hash1, hash2);
        return (64.0 - dist) / 64.0;
    }

    /**
     * Formats a 64-bit hash as a zero-padded 64-character binary string.
     */
    public static String toBinaryString(long hash) {
        String binary = Long.toBinaryString(hash);
        if (binary.length() >= 64) {
            return binary;
        }
        return "0".repeat(64 - binary.length()) + binary;
    }
}
