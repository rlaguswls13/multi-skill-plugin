package com.msp.minhash;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * MinHash generator for computing K-dimensional MinHash signatures from token sets.
 */
public final class MinHasher {

    public static final int DEFAULT_NUM_HASHES = 128;
    private static final long FNV_64_INIT = 0xcbf29ce484222325L;
    private static final long FNV_64_PRIME = 0x100000001b3L;

    private final int numHashes;
    private final long[] seeds;

    /**
     * Creates a MinHasher with default 128 hash permutations and deterministic default seed.
     */
    public MinHasher() {
        this(DEFAULT_NUM_HASHES, 42L);
    }

    /**
     * Creates a MinHasher with specified number of hash permutations and random seed.
     */
    public MinHasher(int numHashes, long masterSeed) {
        if (numHashes <= 0) {
            throw new IllegalArgumentException("numHashes must be positive, got: " + numHashes);
        }
        this.numHashes = numHashes;
        this.seeds = new long[numHashes];
        Random rnd = new Random(masterSeed);
        for (int i = 0; i < numHashes; i++) {
            long s = rnd.nextLong();
            while (s == 0) {
                s = rnd.nextLong();
            }
            this.seeds[i] = s;
        }
    }

    public int getNumHashes() {
        return numHashes;
    }

    /**
     * Computes the MinHash signature for a given set of tokens.
     */
    public MinHashSignature computeSignature(Set<String> tokens) {
        long[] minHashes = new long[numHashes];
        Arrays.fill(minHashes, Long.MAX_VALUE);

        if (tokens == null || tokens.isEmpty()) {
            Arrays.fill(minHashes, 0L);
            return new MinHashSignature(minHashes);
        }

        for (String token : tokens) {
            long tokenHash = hashToken(token);
            for (int i = 0; i < numHashes; i++) {
                long h = mix64(tokenHash ^ seeds[i]);
                if (h < minHashes[i]) {
                    minHashes[i] = h;
                }
            }
        }

        return new MinHashSignature(minHashes);
    }

    /**
     * Estimates Jaccard similarity between two token sets using MinHash.
     */
    public double estimateSimilarity(Set<String> tokensA, Set<String> tokensB) {
        MinHashSignature sigA = computeSignature(tokensA);
        MinHashSignature sigB = computeSignature(tokensB);
        return sigA.similarity(sigB);
    }

    /**
     * Calculates the true exact Jaccard similarity between any two sets: |A ∩ B| / |A ∪ B|.
     */
    public static <T> double exactJaccard(Set<T> setA, Set<T> setB) {
        if (setA == null || setB == null || setA.isEmpty() && setB.isEmpty()) {
            return 1.0;
        }
        if (setA.isEmpty() || setB.isEmpty()) {
            return 0.0;
        }
        Set<T> smaller = setA.size() <= setB.size() ? setA : setB;
        Set<T> larger = setA.size() <= setB.size() ? setB : setA;

        int intersection = 0;
        for (T item : smaller) {
            if (larger.contains(item)) {
                intersection++;
            }
        }
        int union = setA.size() + setB.size() - intersection;
        return union == 0 ? 1.0 : (double) intersection / union;
    }

    private static long hashToken(String token) {
        byte[] bytes = token.getBytes(StandardCharsets.UTF_8);
        long hash = FNV_64_INIT;
        for (byte b : bytes) {
            hash ^= (b & 0xff);
            hash *= FNV_64_PRIME;
        }
        return hash;
    }

    private static long mix64(long z) {
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }
}
