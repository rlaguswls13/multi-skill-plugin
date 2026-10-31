package com.msp.radixtree;

import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * High-performance, memory-compressed Radix / Patricia Trie for prefix matching,
 * routing, and autocomplete.
 *
 * <p>Thread-safe implementation utilizing fine-grained Read/Write locking,
 * automatic edge-splitting on insertion, and node-merging on removal.</p>
 *
 * @param <V> the type of values stored in the tree
 */
public class RadixTree<V> {

    private static class Node<V> {
        String edgeLabel;
        boolean hasValue;
        V value;
        final Map<Character, Node<V>> children = new TreeMap<>();

        Node(String edgeLabel, boolean hasValue, V value) {
            this.edgeLabel = edgeLabel;
            this.hasValue = hasValue;
            this.value = value;
        }
    }

    private final Node<V> root = new Node<>("", false, null);
    private int size = 0;
    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock.ReadLock rLock = rwLock.readLock();
    private final ReentrantReadWriteLock.WriteLock wLock = rwLock.writeLock();

    public RadixTree() {}

    /**
     * Associates the specified value with the specified key.
     *
     * @param key   the key with which the specified value is to be associated
     * @param value the value to be associated with the specified key
     */
    public void put(String key, V value) {
        Objects.requireNonNull(key, "Key must not be null");
        wLock.lock();
        try {
            if (key.isEmpty()) {
                if (!root.hasValue) {
                    size++;
                }
                root.hasValue = true;
                root.value = value;
                return;
            }

            Node<V> current = root;
            String remaining = key;

            while (true) {
                char firstChar = remaining.charAt(0);
                Node<V> child = current.children.get(firstChar);

                if (child == null) {
                    // No existing edge starts with this char, create a new leaf
                    Node<V> newNode = new Node<>(remaining, true, value);
                    current.children.put(firstChar, newNode);
                    size++;
                    return;
                }

                int commonLen = commonPrefixLength(child.edgeLabel, remaining);

                if (commonLen < child.edgeLabel.length()) {
                    // Edge-splitting required
                    String commonPrefix = child.edgeLabel.substring(0, commonLen);
                    String childSuffix = child.edgeLabel.substring(commonLen);

                    Node<V> splitNode = new Node<>(commonPrefix, false, null);
                    child.edgeLabel = childSuffix;
                    splitNode.children.put(childSuffix.charAt(0), child);

                    current.children.put(firstChar, splitNode);

                    if (commonLen == remaining.length()) {
                        splitNode.hasValue = true;
                        splitNode.value = value;
                        size++;
                    } else {
                        String newSuffix = remaining.substring(commonLen);
                        Node<V> newLeaf = new Node<>(newSuffix, true, value);
                        splitNode.children.put(newSuffix.charAt(0), newLeaf);
                        size++;
                    }
                    return;
                } else {
                    // child.edgeLabel is fully matched
                    if (commonLen == remaining.length()) {
                        if (!child.hasValue) {
                            size++;
                        }
                        child.hasValue = true;
                        child.value = value;
                        return;
                    }
                    current = child;
                    remaining = remaining.substring(commonLen);
                }
            }
        } finally {
            wLock.unlock();
        }
    }

    /**
     * Returns the value to which the specified key is mapped, or {@code null} if this tree contains no mapping for the key.
     */
    public V get(String key) {
        Objects.requireNonNull(key, "Key must not be null");
        rLock.lock();
        try {
            if (key.isEmpty()) {
                return root.hasValue ? root.value : null;
            }

            Node<V> current = root;
            String remaining = key;

            while (!remaining.isEmpty()) {
                char firstChar = remaining.charAt(0);
                Node<V> child = current.children.get(firstChar);
                if (child == null || !remaining.startsWith(child.edgeLabel)) {
                    return null;
                }
                remaining = remaining.substring(child.edgeLabel.length());
                current = child;
            }

            return current.hasValue ? current.value : null;
        } finally {
            rLock.unlock();
        }
    }

    /**
     * Returns {@code true} if this tree contains a mapping for the specified key.
     */
    public boolean containsKey(String key) {
        return get(key) != null;
    }

    private record PathFrame<V>(Node<V> parent, Node<V> node, char branchChar) {}

    /**
     * Removes the mapping for a key from this tree if it is present.
     * Merges single-child nodes automatically to maintain Radix tree compactness.
     */
    public V remove(String key) {
        Objects.requireNonNull(key, "Key must not be null");
        wLock.lock();
        try {
            if (key.isEmpty()) {
                if (!root.hasValue) {
                    return null;
                }
                V old = root.value;
                root.hasValue = false;
                root.value = null;
                size--;
                return old;
            }

            List<PathFrame<V>> path = new ArrayList<>();
            Node<V> current = root;
            String remaining = key;

            while (!remaining.isEmpty()) {
                char firstChar = remaining.charAt(0);
                Node<V> child = current.children.get(firstChar);
                if (child == null || !remaining.startsWith(child.edgeLabel)) {
                    return null;
                }
                path.add(new PathFrame<>(current, child, firstChar));
                remaining = remaining.substring(child.edgeLabel.length());
                current = child;
            }

            if (!current.hasValue) {
                return null;
            }

            V oldValue = current.value;
            current.hasValue = false;
            current.value = null;
            size--;

            // Clean up and merge nodes bottom-up
            for (int i = path.size() - 1; i >= 0; i--) {
                PathFrame<V> frame = path.get(i);
                Node<V> parent = frame.parent();
                Node<V> node = frame.node();
                char branchChar = frame.branchChar();

                if (node.children.isEmpty() && !node.hasValue) {
                    // Leaf with no value, prune it
                    parent.children.remove(branchChar);
                } else if (node.children.size() == 1 && !node.hasValue) {
                    // Single child without value, merge node with its only child
                    Node<V> onlyChild = node.children.values().iterator().next();
                    onlyChild.edgeLabel = node.edgeLabel + onlyChild.edgeLabel;
                    parent.children.put(branchChar, onlyChild);
                }
            }

            return oldValue;
        } finally {
            wLock.unlock();
        }
    }

    /**
     * Returns all values whose keys start with the specified prefix.
     */
    public List<V> searchByPrefix(String prefix) {
        List<Map.Entry<String, V>> entries = entriesWithPrefix(prefix);
        List<V> values = new ArrayList<>(entries.size());
        for (Map.Entry<String, V> entry : entries) {
            values.add(entry.getValue());
        }
        return values;
    }

    /**
     * Returns all key-value entries whose keys start with the specified prefix.
     */
    public List<Map.Entry<String, V>> entriesWithPrefix(String prefix) {
        Objects.requireNonNull(prefix, "Prefix must not be null");
        rLock.lock();
        try {
            List<Map.Entry<String, V>> results = new ArrayList<>();
            if (prefix.isEmpty()) {
                collectSubtree(root, "", results);
                return results;
            }

            Node<V> current = root;
            String remaining = prefix;
            StringBuilder matchedPrefix = new StringBuilder();

            while (true) {
                char firstChar = remaining.charAt(0);
                Node<V> child = current.children.get(firstChar);
                if (child == null) {
                    return List.of();
                }

                int commonLen = commonPrefixLength(child.edgeLabel, remaining);
                if (commonLen < remaining.length()) {
                    if (commonLen < child.edgeLabel.length()) {
                        // Diverged
                        return List.of();
                    }
                    matchedPrefix.append(child.edgeLabel);
                    remaining = remaining.substring(commonLen);
                    current = child;
                } else {
                    // remaining is completely covered by child.edgeLabel
                    matchedPrefix.append(child.edgeLabel);
                    collectSubtree(child, matchedPrefix.toString(), results);
                    return results;
                }
            }
        } finally {
            rLock.unlock();
        }
    }

    private void collectSubtree(Node<V> node, String currentKey, List<Map.Entry<String, V>> results) {
        if (node.hasValue) {
            results.add(Map.entry(currentKey, node.value));
        }
        for (Node<V> child : node.children.values()) {
            collectSubtree(child, currentKey + child.edgeLabel, results);
        }
    }

    /**
     * Returns the longest key in the tree that is a prefix of the specified key.
     *
     * @param key the input key
     * @return the longest matching prefix key, or {@code null} if no prefix matched
     */
    public String longestPrefixOf(String key) {
        Objects.requireNonNull(key, "Key must not be null");
        rLock.lock();
        try {
            String longest = root.hasValue ? "" : null;
            if (key.isEmpty()) {
                return longest;
            }

            Node<V> current = root;
            String remaining = key;
            StringBuilder accumulated = new StringBuilder();

            while (!remaining.isEmpty()) {
                char firstChar = remaining.charAt(0);
                Node<V> child = current.children.get(firstChar);
                if (child == null || !remaining.startsWith(child.edgeLabel)) {
                    break;
                }
                accumulated.append(child.edgeLabel);
                remaining = remaining.substring(child.edgeLabel.length());
                if (child.hasValue) {
                    longest = accumulated.toString();
                }
                current = child;
            }

            return longest;
        } finally {
            rLock.unlock();
        }
    }

    /**
     * Returns the number of key-value mappings in this tree.
     */
    public int size() {
        rLock.lock();
        try {
            return size;
        } finally {
            rLock.unlock();
        }
    }

    /**
     * Returns {@code true} if this tree contains no key-value mappings.
     */
    public boolean isEmpty() {
        return size() == 0;
    }

    /**
     * Removes all of the mappings from this tree.
     */
    public void clear() {
        wLock.lock();
        try {
            root.children.clear();
            root.hasValue = false;
            root.value = null;
            size = 0;
        } finally {
            wLock.unlock();
        }
    }

    private static int commonPrefixLength(String a, String b) {
        int max = Math.min(a.length(), b.length());
        int i = 0;
        while (i < max && a.charAt(i) == b.charAt(i)) {
            i++;
        }
        return i;
    }
}
