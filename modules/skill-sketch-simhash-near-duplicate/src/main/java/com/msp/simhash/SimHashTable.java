package com.msp.simhash;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Sub-linear SimHash Table index partitioned into $B$ blocks (default 4 blocks of 16-bit keys).
 * By the Pigeonhole Principle, if two 64-bit SimHashes differ by at most $k$ bits, and the hash
 * is partitioned into $k + 1$ blocks, at least one block will match identically.
 * This yields $O(1)$ sub-linear candidate lookup before computing exact Hamming distances.
 *
 * @param <T> Type of the stored document or metadata
 */
public class SimHashTable<T> {

    public record Entry<T>(long hash, T value) {}

    public record SearchResult<T>(T value, long hash, int distance) implements Comparable<SearchResult<T>> {
        @Override
        public int compareTo(SearchResult<T> o) {
            int cmp = Integer.compare(this.distance, o.distance);
            if (cmp != 0) return cmp;
            return Long.compare(this.hash, o.hash);
        }
    }

    private final int numBlocks;
    private final int bitsPerBlock;
    private final long blockMask;
    private final List<Map<Long, Set<Entry<T>>>> blockTables;
    private final Set<Entry<T>> allEntries;

    /**
     * Creates a SimHashTable with default 4 blocks (16 bits each), ideal for max distance k <= 3.
     */
    public SimHashTable() {
        this(4);
    }

    /**
     * Creates a SimHashTable partitioned into {@code numBlocks}.
     *
     * @param numBlocks Number of blocks (must divide 64 evenly, e.g. 2, 4, 8, 16)
     */
    public SimHashTable(int numBlocks) {
        if (numBlocks <= 0 || (64 % numBlocks != 0)) {
            throw new IllegalArgumentException("numBlocks must be a positive divisor of 64 (e.g. 2, 4, 8, 16)");
        }
        this.numBlocks = numBlocks;
        this.bitsPerBlock = 64 / numBlocks;
        this.blockMask = (1L << bitsPerBlock) - 1L;

        this.blockTables = new ArrayList<>(numBlocks);
        for (int i = 0; i < numBlocks; i++) {
            this.blockTables.add(new ConcurrentHashMap<>());
        }
        this.allEntries = new CopyOnWriteArraySet<>();
    }

    /**
     * Inserts an item with its precomputed 64-bit SimHash.
     *
     * @param hash  64-bit SimHash
     * @param value Document or payload item
     */
    public void put(long hash, T value) {
        Entry<T> entry = new Entry<>(hash, value);
        allEntries.add(entry);

        for (int b = 0; b < numBlocks; b++) {
            long blockKey = extractBlock(hash, b);
            blockTables.get(b)
                    .computeIfAbsent(blockKey, k -> new CopyOnWriteArraySet<>())
                    .add(entry);
        }
    }

    /**
     * Inserts a document text directly by computing its SimHash.
     *
     * @param document Document text
     * @param value    Payload
     * @return Computed 64-bit SimHash
     */
    public long put(String document, T value) {
        long hash = SimHash.compute(document);
        put(hash, value);
        return hash;
    }

    /**
     * Removes an item with the given hash from the index.
     *
     * @param hash  64-bit SimHash
     * @param value Document item
     * @return true if removed, false if not found
     */
    public boolean remove(long hash, T value) {
        Entry<T> entry = new Entry<>(hash, value);
        boolean removed = allEntries.remove(entry);
        if (removed) {
            for (int b = 0; b < numBlocks; b++) {
                long blockKey = extractBlock(hash, b);
                Set<Entry<T>> bucket = blockTables.get(b).get(blockKey);
                if (bucket != null) {
                    bucket.remove(entry);
                    if (bucket.isEmpty()) {
                        blockTables.get(b).remove(blockKey, Collections.emptySet());
                    }
                }
            }
        }
        return removed;
    }

    /**
     * Finds all items in the index whose Hamming distance is <= maxHammingDistance.
     *
     * @param queryHash          64-bit SimHash to search
     * @param maxHammingDistance Maximum allowed bit difference
     * @return Sorted list of matching results (closest distance first)
     */
    public List<SearchResult<T>> findNearDuplicates(long queryHash, int maxHammingDistance) {
        if (maxHammingDistance < 0) {
            return Collections.emptyList();
        }

        // If maxHammingDistance < numBlocks, we can guarantee block matching via Pigeonhole Principle
        Set<Entry<T>> candidateEntries;
        if (maxHammingDistance < numBlocks) {
            candidateEntries = new HashSet<>();
            for (int b = 0; b < numBlocks; b++) {
                long blockKey = extractBlock(queryHash, b);
                Set<Entry<T>> bucket = blockTables.get(b).get(blockKey);
                if (bucket != null) {
                    candidateEntries.addAll(bucket);
                }
            }
        } else {
            // Fallback to scanning all entries if maxHammingDistance exceeds block guarantee
            candidateEntries = allEntries;
        }

        List<SearchResult<T>> matches = new ArrayList<>();
        for (Entry<T> candidate : candidateEntries) {
            int distance = SimHash.hammingDistance(queryHash, candidate.hash());
            if (distance <= maxHammingDistance) {
                matches.add(new SearchResult<>(candidate.value(), candidate.hash(), distance));
            }
        }

        Collections.sort(matches);
        return matches;
    }

    /**
     * Finds near duplicates for a given document text.
     */
    public List<SearchResult<T>> findNearDuplicates(String document, int maxHammingDistance) {
        return findNearDuplicates(SimHash.compute(document), maxHammingDistance);
    }

    /**
     * Returns the total number of indexed entries.
     */
    public int size() {
        return allEntries.size();
    }

    /**
     * Clears all entries from the index.
     */
    public void clear() {
        allEntries.clear();
        for (Map<Long, Set<Entry<T>>> table : blockTables) {
            table.clear();
        }
    }

    private long extractBlock(long hash, int blockIndex) {
        return (hash >>> (blockIndex * bitsPerBlock)) & blockMask;
    }
}
