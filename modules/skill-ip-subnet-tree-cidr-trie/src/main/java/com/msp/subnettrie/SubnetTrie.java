package com.msp.subnettrie;

import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * High-performance Binary Radix Trie storing IPv4 and IPv6 CIDR subnet blocks.
 *
 * <p>Supports:</p>
 * <ul>
 *   <li>Longest Prefix Match (LPM) IP address lookup</li>
 *   <li>CIDR block overlap detection (containment and sub-range intersection)</li>
 *   <li>All matching enclosing subnets query</li>
 *   <li>Exact CIDR search and removal</li>
 * </ul>
 *
 * @param <T> payload type associated with each subnet
 */
public final class SubnetTrie<T> {

    private static class Node<T> {
        Node<T> left;  // bit 0
        Node<T> right; // bit 1
        T value;
        String cidr;
        boolean hasValue;

        Node<T> getChild(int bit) {
            return bit == 0 ? left : right;
        }

        void setChild(int bit, Node<T> child) {
            if (bit == 0) {
                left = child;
            } else {
                right = child;
            }
        }

        boolean isLeaf() {
            return left == null && right == null;
        }
    }

    private final Node<T> rootV4 = new Node<>();
    private final Node<T> rootV6 = new Node<>();
    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
    private int size = 0;

    public SubnetTrie() {
    }

    public int size() {
        rwLock.readLock().lock();
        try {
            return size;
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public boolean isEmpty() {
        return size() == 0;
    }

    public void clear() {
        rwLock.writeLock().lock();
        try {
            rootV4.left = null;
            rootV4.right = null;
            rootV4.value = null;
            rootV4.cidr = null;
            rootV4.hasValue = false;

            rootV6.left = null;
            rootV6.right = null;
            rootV6.value = null;
            rootV6.cidr = null;
            rootV6.hasValue = false;

            size = 0;
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * Inserts a CIDR prefix and associated data payload into the trie.
     */
    public void insert(String cidrStr, T data) {
        Objects.requireNonNull(cidrStr, "cidrStr must not be null");
        Objects.requireNonNull(data, "data must not be null");
        CidrPrefix prefix = CidrPrefix.parse(cidrStr);

        rwLock.writeLock().lock();
        try {
            Node<T> curr = prefix.isIpv6() ? rootV6 : rootV4;
            int len = prefix.getPrefixLength();
            for (int i = 0; i < len; i++) {
                int bit = prefix.getBit(i);
                Node<T> child = curr.getChild(bit);
                if (child == null) {
                    child = new Node<>();
                    curr.setChild(bit, child);
                }
                curr = child;
            }

            if (!curr.hasValue) {
                size++;
            }
            curr.hasValue = true;
            curr.value = data;
            curr.cidr = prefix.getOriginal();
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * Finds exact data associated with the given CIDR prefix.
     */
    public Optional<T> findExact(String cidrStr) {
        Objects.requireNonNull(cidrStr, "cidrStr must not be null");
        CidrPrefix prefix = CidrPrefix.parse(cidrStr);

        rwLock.readLock().lock();
        try {
            Node<T> curr = prefix.isIpv6() ? rootV6 : rootV4;
            int len = prefix.getPrefixLength();
            for (int i = 0; i < len; i++) {
                int bit = prefix.getBit(i);
                curr = curr.getChild(bit);
                if (curr == null) {
                    return Optional.empty();
                }
            }
            return curr.hasValue ? Optional.ofNullable(curr.value) : Optional.empty();
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * Finds the longest prefix match (LPM) subnet containing the given IP address.
     */
    public Optional<T> findLongestMatchingSubnet(String ipStr) {
        Objects.requireNonNull(ipStr, "ipStr must not be null");
        IpParser.ParsedIp ip = IpParser.parseIp(ipStr);

        rwLock.readLock().lock();
        try {
            Node<T> curr = ip.isIpv6() ? rootV6 : rootV4;
            T longestMatch = curr.hasValue ? curr.value : null;

            int maxBits = ip.isIpv6() ? 128 : 32;
            for (int i = 0; i < maxBits; i++) {
                int bit = ip.getBit(i);
                curr = curr.getChild(bit);
                if (curr == null) {
                    break;
                }
                if (curr.hasValue) {
                    longestMatch = curr.value;
                }
            }
            return Optional.ofNullable(longestMatch);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * Returns true if any subnet in the trie contains the given IP.
     */
    public boolean containsIp(String ipStr) {
        return findLongestMatchingSubnet(ipStr).isPresent();
    }

    /**
     * Finds all enclosing subnets containing the given IP, ordered from shortest to longest prefix.
     */
    public List<T> findAllMatchingSubnets(String ipStr) {
        Objects.requireNonNull(ipStr, "ipStr must not be null");
        IpParser.ParsedIp ip = IpParser.parseIp(ipStr);

        rwLock.readLock().lock();
        try {
            List<T> matches = new ArrayList<>();
            Node<T> curr = ip.isIpv6() ? rootV6 : rootV4;
            if (curr.hasValue) {
                matches.add(curr.value);
            }

            int maxBits = ip.isIpv6() ? 128 : 32;
            for (int i = 0; i < maxBits; i++) {
                int bit = ip.getBit(i);
                curr = curr.getChild(bit);
                if (curr == null) {
                    break;
                }
                if (curr.hasValue) {
                    matches.add(curr.value);
                }
            }
            return matches;
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * Finds all subnets in the trie that overlap with the specified CIDR block.
     * (Either an ancestor containing it, exact match, or descendants inside it).
     */
    public List<String> findOverlappingSubnets(String cidrStr) {
        Objects.requireNonNull(cidrStr, "cidrStr must not be null");
        CidrPrefix prefix = CidrPrefix.parse(cidrStr);

        rwLock.readLock().lock();
        try {
            List<String> overlapping = new ArrayList<>();
            Node<T> curr = prefix.isIpv6() ? rootV6 : rootV4;

            // 1. Check root
            if (curr.hasValue) {
                overlapping.add(curr.cidr);
            }

            int len = prefix.getPrefixLength();
            boolean reachedPrefixNode = true;
            for (int i = 0; i < len; i++) {
                int bit = prefix.getBit(i);
                curr = curr.getChild(bit);
                if (curr == null) {
                    reachedPrefixNode = false;
                    break;
                }
                if (i < len - 1 && curr.hasValue) {
                    overlapping.add(curr.cidr); // Ancestor subnet containing this CIDR
                }
            }

            if (reachedPrefixNode && curr != null) {
                // Collect all subnets in subtree rooted at curr (including curr itself)
                collectSubtree(curr, overlapping);
            }

            return overlapping;
        } finally {
            rwLock.readLock().unlock();
        }
    }

    private void collectSubtree(Node<T> node, List<String> result) {
        if (node == null) return;
        if (node.hasValue) {
            result.add(node.cidr);
        }
        collectSubtree(node.left, result);
        collectSubtree(node.right, result);
    }

    /**
     * Removes an exact CIDR block from the trie and prunes dead branches.
     */
    public boolean remove(String cidrStr) {
        Objects.requireNonNull(cidrStr, "cidrStr must not be null");
        CidrPrefix prefix = CidrPrefix.parse(cidrStr);

        rwLock.writeLock().lock();
        try {
            Node<T> root = prefix.isIpv6() ? rootV6 : rootV4;
            boolean[] removed = new boolean[1];
            removeHelper(root, prefix, 0, removed);
            if (removed[0]) {
                size--;
            }
            return removed[0];
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    private boolean removeHelper(Node<T> curr, CidrPrefix prefix, int bitIndex, boolean[] removed) {
        if (curr == null) {
            return false;
        }
        if (bitIndex == prefix.getPrefixLength()) {
            if (curr.hasValue) {
                curr.hasValue = false;
                curr.value = null;
                curr.cidr = null;
                removed[0] = true;
            }
            return curr.isLeaf();
        }

        int bit = prefix.getBit(bitIndex);
        Node<T> child = curr.getChild(bit);
        boolean shouldPrune = removeHelper(child, prefix, bitIndex + 1, removed);

        if (shouldPrune) {
            curr.setChild(bit, null);
        }

        return !curr.hasValue && curr.isLeaf();
    }
}
