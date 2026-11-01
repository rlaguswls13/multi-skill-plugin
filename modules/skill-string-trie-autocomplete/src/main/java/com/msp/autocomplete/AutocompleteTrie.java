package com.msp.autocomplete;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Thread-safe, Unicode-supporting Trie engine for Top-K prefix autocomplete and frequency/weight ranking.
 */
public class AutocompleteTrie {

    private static final class Node {
        final Map<Integer, Node> children = new HashMap<>();
        String word;
        Double score;

        boolean isTerminal() {
            return score != null;
        }
    }

    private final Node root = new Node();
    private int size = 0;
    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();

    /**
     * Inserts or overwrites a word with the specified score.
     *
     * @param word  the word to insert (supports any Unicode string)
     * @param score the ranking score
     */
    public void insert(String word, double score) {
        Objects.requireNonNull(word, "word must not be null");
        rwLock.writeLock().lock();
        try {
            Node current = root;
            int[] codePoints = word.codePoints().toArray();
            for (int cp : codePoints) {
                current = current.children.computeIfAbsent(cp, k -> new Node());
            }
            if (!current.isTerminal()) {
                size++;
            }
            current.word = word;
            current.score = score;
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * Increments the score of a word by the given delta.
     * If the word does not exist, it is created with the given delta as its initial score.
     *
     * @param word  the word whose score should be adjusted
     * @param delta the amount to add to the word's current score
     */
    public void incrementScore(String word, double delta) {
        Objects.requireNonNull(word, "word must not be null");
        rwLock.writeLock().lock();
        try {
            Node current = root;
            int[] codePoints = word.codePoints().toArray();
            for (int cp : codePoints) {
                current = current.children.computeIfAbsent(cp, k -> new Node());
            }
            if (!current.isTerminal()) {
                current.score = delta;
                current.word = word;
                size++;
            } else {
                current.score = current.score + delta;
            }
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * Retrieves up to {@code maxResults} top autocomplete suggestions matching the given prefix,
     * ordered descending by score.
     *
     * @param prefix     the search prefix (null or empty matches all words in trie)
     * @param maxResults maximum number of top suggestions to return
     * @return unmodifiable list of matching {@link AutocompleteItem}s
     */
    public List<AutocompleteItem> suggest(String prefix, int maxResults) {
        if (maxResults <= 0) {
            return List.of();
        }
        String pfx = prefix != null ? prefix : "";

        rwLock.readLock().lock();
        try {
            Node current = root;
            if (!pfx.isEmpty()) {
                int[] codePoints = pfx.codePoints().toArray();
                for (int cp : codePoints) {
                    current = current.children.get(cp);
                    if (current == null) {
                        return List.of();
                    }
                }
            }

            List<AutocompleteItem> collected = new ArrayList<>();
            collectDescendants(current, collected);
            Collections.sort(collected); // natural order: descending score, ascending text

            if (collected.size() > maxResults) {
                return List.copyOf(collected.subList(0, maxResults));
            }
            return List.copyOf(collected);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    private void collectDescendants(Node node, List<AutocompleteItem> output) {
        if (node.isTerminal()) {
            output.add(new AutocompleteItem(node.word, node.score));
        }
        for (Node child : node.children.values()) {
            collectDescendants(child, output);
        }
    }

    /**
     * Removes a word from the trie.
     *
     * @param word the word to remove
     * @return {@code true} if the word was found and removed; {@code false} otherwise
     */
    public boolean remove(String word) {
        Objects.requireNonNull(word, "word must not be null");
        rwLock.writeLock().lock();
        try {
            int[] codePoints = word.codePoints().toArray();
            boolean removed = prune(root, codePoints, 0);
            if (removed) {
                size--;
            }
            return removed;
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    private boolean prune(Node current, int[] codePoints, int index) {
        if (index == codePoints.length) {
            if (!current.isTerminal()) {
                return false;
            }
            current.score = null;
            current.word = null;
            return true;
        }

        int cp = codePoints[index];
        Node next = current.children.get(cp);
        if (next == null) {
            return false;
        }

        boolean removed = prune(next, codePoints, index + 1);
        if (removed && !next.isTerminal() && next.children.isEmpty()) {
            current.children.remove(cp);
        }
        return removed;
    }

    /**
     * Retrieves the score for the given exact word if it exists.
     *
     * @param word the exact word to query
     * @return an {@link OptionalDouble} containing the score if present, or empty
     */
    public OptionalDouble getScore(String word) {
        Objects.requireNonNull(word, "word must not be null");
        rwLock.readLock().lock();
        try {
            Node current = root;
            int[] codePoints = word.codePoints().toArray();
            for (int cp : codePoints) {
                current = current.children.get(cp);
                if (current == null) {
                    return OptionalDouble.empty();
                }
            }
            if (current.isTerminal()) {
                return OptionalDouble.of(current.score);
            }
            return OptionalDouble.empty();
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * Returns the number of distinct words stored in the trie.
     *
     * @return word count
     */
    public int size() {
        rwLock.readLock().lock();
        try {
            return size;
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * Clears all words and resets the trie.
     */
    public void clear() {
        rwLock.writeLock().lock();
        try {
            root.children.clear();
            root.word = null;
            root.score = null;
            size = 0;
        } finally {
            rwLock.writeLock().unlock();
        }
    }
}
