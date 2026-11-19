package com.msp.hpack;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Dynamic Table for HPACK header compression (RFC 7541 Section 4).
 * Stores recently encoded/decoded headers with FIFO eviction based on byte size.
 */
public class HpackDynamicTable {

    public static final int DEFAULT_MAX_CAPACITY = 4096;

    private int maxCapacity;
    private int currentSize;
    private final List<HeaderField> entries;

    public HpackDynamicTable(int maxCapacity) {
        if (maxCapacity < 0) {
            throw new IllegalArgumentException("Max capacity cannot be negative: " + maxCapacity);
        }
        this.maxCapacity = maxCapacity;
        this.currentSize = 0;
        this.entries = new ArrayList<>();
    }

    public HpackDynamicTable() {
        this(DEFAULT_MAX_CAPACITY);
    }

    public synchronized int getMaxCapacity() {
        return maxCapacity;
    }

    public synchronized void setMaxCapacity(int newCapacity) {
        if (newCapacity < 0) {
            throw new IllegalArgumentException("Max capacity cannot be negative: " + newCapacity);
        }
        this.maxCapacity = newCapacity;
        evictIfNeeded();
    }

    public synchronized int getCurrentSize() {
        return currentSize;
    }

    public synchronized int length() {
        return entries.size();
    }

    /**
     * Adds an entry to the dynamic table at the head.
     * Evicts oldest entries if currentSize + entrySize exceeds maxCapacity.
     */
    public synchronized void add(HeaderField field) {
        int entrySize = field.size();
        if (entrySize > maxCapacity) {
            clear();
            return;
        }

        while (currentSize + entrySize > maxCapacity && !entries.isEmpty()) {
            HeaderField removed = entries.remove(entries.size() - 1);
            currentSize -= removed.size();
        }

        entries.add(0, field);
        currentSize += entrySize;
    }

    /**
     * Retrieves an entry by dynamic table 0-based offset (0 = newest).
     */
    public synchronized HeaderField getRelative(int index) {
        if (index < 0 || index >= entries.size()) {
            throw new IndexOutOfBoundsException("Dynamic table index out of bounds: " + index);
        }
        return entries.get(index);
    }

    /**
     * Retrieves an entry by 1-based HPACK absolute index (static table size + 1 ...).
     */
    public synchronized HeaderField get(int hpackIndex) {
        int relative = hpackIndex - (HpackStaticTable.size() + 1);
        return getRelative(relative);
    }

    /**
     * Finds the absolute HPACK index (>= 62) for an exact (name, value) match, or -1.
     */
    public synchronized int findExact(String name, String value) {
        for (int i = 0; i < entries.size(); i++) {
            HeaderField entry = entries.get(i);
            if (entry.name().equals(name) && entry.value().equals(value)) {
                return HpackStaticTable.size() + 1 + i;
            }
        }
        return -1;
    }

    /**
     * Finds the absolute HPACK index (>= 62) for a name match, or -1.
     */
    public synchronized int findName(String name) {
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).name().equals(name)) {
                return HpackStaticTable.size() + 1 + i;
            }
        }
        return -1;
    }

    public synchronized void clear() {
        entries.clear();
        currentSize = 0;
    }

    private void evictIfNeeded() {
        while (currentSize > maxCapacity && !entries.isEmpty()) {
            HeaderField removed = entries.remove(entries.size() - 1);
            currentSize -= removed.size();
        }
    }
}
