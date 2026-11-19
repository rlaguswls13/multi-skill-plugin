package com.msp.minhash;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Locality-Sensitive Hashing (LSH) index using Bands and Rows technique
 * for sub-linear similarity search across MinHash signatures.
 */
public final class LshIndex {

    private final MinHasher minHasher;
    private final int numBands;
    private final int rowsPerBand;
    private final ConcurrentMap<String, MinHashSignature> docSignatures = new ConcurrentHashMap<>();
    private final ConcurrentMap<BandBucketKey, Set<String>> buckets = new ConcurrentHashMap<>();

    private record BandBucketKey(int bandIndex, long bandHash) {
    }

    /**
     * Creates an LSH index with default MinHasher (128 hashes), 16 bands, and 8 rows per band.
     * Theoretical threshold is approx (1/16)^(1/8) ≈ 0.707.
     */
    public LshIndex() {
        this(new MinHasher(128, 42L), 16, 8);
    }

    /**
     * Creates an LSH index with custom MinHasher, number of bands, and rows per band.
     * Note: numBands * rowsPerBand must equal minHasher.getNumHashes().
     */
    public LshIndex(MinHasher minHasher, int numBands, int rowsPerBand) {
        Objects.requireNonNull(minHasher, "minHasher must not be null");
        if (numBands <= 0 || rowsPerBand <= 0) {
            throw new IllegalArgumentException("numBands and rowsPerBand must be positive");
        }
        if (numBands * rowsPerBand != minHasher.getNumHashes()) {
            throw new IllegalArgumentException("numBands (" + numBands + ") * rowsPerBand ("
                    + rowsPerBand + ") must equal minHasher.getNumHashes() ("
                    + minHasher.getNumHashes() + ")");
        }
        this.minHasher = minHasher;
        this.numBands = numBands;
        this.rowsPerBand = rowsPerBand;
    }

    public MinHasher getMinHasher() {
        return minHasher;
    }

    public int getNumBands() {
        return numBands;
    }

    public int getRowsPerBand() {
        return rowsPerBand;
    }

    public int size() {
        return docSignatures.size();
    }

    public Optional<MinHashSignature> getSignature(String docId) {
        return Optional.ofNullable(docSignatures.get(docId));
    }

    /**
     * Indexes a document given its raw token set.
     */
    public void index(String docId, Set<String> tokens) {
        Objects.requireNonNull(docId, "docId must not be null");
        MinHashSignature signature = minHasher.computeSignature(tokens);
        index(docId, signature);
    }

    /**
     * Indexes a document given its pre-computed MinHash signature.
     */
    public void index(String docId, MinHashSignature signature) {
        Objects.requireNonNull(docId, "docId must not be null");
        Objects.requireNonNull(signature, "signature must not be null");
        if (signature.size() != minHasher.getNumHashes()) {
            throw new IllegalArgumentException("Signature dimension " + signature.size()
                    + " does not match index dimension " + minHasher.getNumHashes());
        }

        // If docId was previously indexed, remove it from old buckets first
        remove(docId);

        docSignatures.put(docId, signature);

        for (int b = 0; b < numBands; b++) {
            long bandHash = computeBandHash(signature, b);
            BandBucketKey key = new BandBucketKey(b, bandHash);
            buckets.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet()).add(docId);
        }
    }

    /**
     * Removes a document from the index.
     */
    public boolean remove(String docId) {
        MinHashSignature oldSig = docSignatures.remove(docId);
        if (oldSig == null) {
            return false;
        }
        for (int b = 0; b < numBands; b++) {
            long bandHash = computeBandHash(oldSig, b);
            BandBucketKey key = new BandBucketKey(b, bandHash);
            Set<String> set = buckets.get(key);
            if (set != null) {
                set.remove(docId);
                if (set.isEmpty()) {
                    buckets.remove(key, Collections.emptySet());
                }
            }
        }
        return true;
    }

    /**
     * Clears all documents and buckets from the index.
     */
    public void clear() {
        docSignatures.clear();
        buckets.clear();
    }

    /**
     * Queries all candidate document IDs that collide in at least one band with the query tokens.
     */
    public Set<String> queryCandidates(Set<String> queryTokens) {
        MinHashSignature qSig = minHasher.computeSignature(queryTokens);
        return queryCandidates(qSig);
    }

    /**
     * Queries all candidate document IDs that collide in at least one band with the query signature.
     */
    public Set<String> queryCandidates(MinHashSignature querySignature) {
        Objects.requireNonNull(querySignature, "querySignature must not be null");
        Set<String> candidates = new HashSet<>();
        for (int b = 0; b < numBands; b++) {
            long bandHash = computeBandHash(querySignature, b);
            BandBucketKey key = new BandBucketKey(b, bandHash);
            Set<String> set = buckets.get(key);
            if (set != null) {
                candidates.addAll(set);
            }
        }
        return candidates;
    }

    /**
     * Queries documents whose estimated Jaccard similarity is >= threshold.
     */
    public Set<String> querySimilar(Set<String> queryTokens, double threshold) {
        MinHashSignature qSig = minHasher.computeSignature(queryTokens);
        return querySimilar(qSig, threshold);
    }

    /**
     * Queries documents whose estimated Jaccard similarity is >= threshold.
     */
    public Set<String> querySimilar(MinHashSignature querySignature, double threshold) {
        Set<String> candidates = queryCandidates(querySignature);
        Set<String> matches = new HashSet<>();
        for (String candidateId : candidates) {
            MinHashSignature candidateSig = docSignatures.get(candidateId);
            if (candidateSig != null) {
                double sim = querySignature.similarity(candidateSig);
                if (sim >= threshold) {
                    matches.add(candidateId);
                }
            }
        }
        return matches;
    }

    /**
     * Queries similar documents and returns their similarity scores sorted in descending order.
     */
    public List<SimilarityResult> querySimilarWithScores(Set<String> queryTokens, double threshold) {
        MinHashSignature qSig = minHasher.computeSignature(queryTokens);
        return querySimilarWithScores(qSig, threshold);
    }

    /**
     * Queries similar documents and returns their similarity scores sorted in descending order.
     */
    public List<SimilarityResult> querySimilarWithScores(MinHashSignature querySignature, double threshold) {
        Set<String> candidates = queryCandidates(querySignature);
        List<SimilarityResult> results = new ArrayList<>();
        for (String candidateId : candidates) {
            MinHashSignature candidateSig = docSignatures.get(candidateId);
            if (candidateSig != null) {
                double sim = querySignature.similarity(candidateSig);
                if (sim >= threshold) {
                    results.add(new SimilarityResult(candidateId, sim));
                }
            }
        }
        Collections.sort(results);
        return results;
    }

    private long computeBandHash(MinHashSignature sig, int bandIndex) {
        int start = bandIndex * rowsPerBand;
        long h = 0x9e3779b97f4a7c15L;
        for (int r = 0; r < rowsPerBand; r++) {
            long val = sig.get(start + r);
            h = (h ^ val) * 0xbf58476d1ce4e5b9L;
        }
        return h;
    }
}
