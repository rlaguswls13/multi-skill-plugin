package com.msp.slidingbytebuffer;

import java.util.Objects;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Thread-safe sliding window byte buffer maintaining the latest N bytes.
 * When the buffer capacity is exceeded, oldest bytes automatically slide out (FIFO).
 * Provides constant-space tail delimiter checking and pattern matching.
 */
public class SlidingByteBuffer {

    private final int capacity;
    private final byte[] buffer;
    private int head; // Index of the oldest element
    private int size; // Number of elements currently held

    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();

    /**
     * Creates a SlidingByteBuffer with the given fixed capacity.
     *
     * @param capacity maximum number of bytes to hold (> 0)
     */
    public SlidingByteBuffer(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be positive, got: " + capacity);
        }
        this.capacity = capacity;
        this.buffer = new byte[capacity];
        this.head = 0;
        this.size = 0;
    }

    /**
     * Appends a single byte. If capacity is reached, the oldest byte is evicted.
     */
    public void append(byte b) {
        rwLock.writeLock().lock();
        try {
            appendInternal(b);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * Appends an entire byte array.
     */
    public void append(byte[] bytes) {
        Objects.requireNonNull(bytes, "Byte array cannot be null");
        append(bytes, 0, bytes.length);
    }

    /**
     * Appends a slice of a byte array.
     */
    public void append(byte[] bytes, int off, int len) {
        Objects.requireNonNull(bytes, "Byte array cannot be null");
        if (off < 0 || len < 0 || off + len > bytes.length) {
            throw new IndexOutOfBoundsException(
                    "Invalid offset/length: off=" + off + ", len=" + len + ", array length=" + bytes.length
            );
        }
        if (len == 0) {
            return;
        }

        rwLock.writeLock().lock();
        try {
            if (len >= capacity) {
                // If appending more than capacity, only the last 'capacity' bytes are retained
                int start = off + len - capacity;
                System.arraycopy(bytes, start, buffer, 0, capacity);
                head = 0;
                size = capacity;
            } else {
                for (int i = 0; i < len; i++) {
                    appendInternal(bytes[off + i]);
                }
            }
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * Checks if the most recently appended bytes match the given sequence (e.g. \r\n\r\n).
     *
     * @param sequence byte sequence to match against the tail
     * @return true if the buffer ends with sequence, false otherwise
     */
    public boolean matchesTail(byte[] sequence) {
        if (sequence == null || sequence.length == 0) {
            return false;
        }

        rwLock.readLock().lock();
        try {
            int seqLen = sequence.length;
            if (size < seqLen) {
                return false;
            }

            for (int i = 0; i < seqLen; i++) {
                int bufIdx = (head + size - seqLen + i) % capacity;
                if (buffer[bufIdx] != sequence[i]) {
                    return false;
                }
            }
            return true;
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * Searches for the first occurrence of the pattern in the buffer.
     *
     * @param pattern pattern byte sequence to search for
     * @return 0-based offset from the start of the current buffer, or -1 if not found
     */
    public int indexOf(byte[] pattern) {
        if (pattern == null || pattern.length == 0) {
            return -1;
        }

        rwLock.readLock().lock();
        try {
            int patLen = pattern.length;
            if (size < patLen) {
                return -1;
            }

            for (int i = 0; i <= size - patLen; i++) {
                boolean match = true;
                for (int j = 0; j < patLen; j++) {
                    int bufIdx = (head + i + j) % capacity;
                    if (buffer[bufIdx] != pattern[j]) {
                        match = false;
                        break;
                    }
                }
                if (match) {
                    return i;
                }
            }
            return -1;
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * Returns a snapshot of the current buffer contents in FIFO order.
     */
    public byte[] toByteArray() {
        rwLock.readLock().lock();
        try {
            byte[] result = new byte[size];
            for (int i = 0; i < size; i++) {
                result[i] = buffer[(head + i) % capacity];
            }
            return result;
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * Returns the current number of bytes stored in the buffer.
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
     * Returns the maximum capacity of the buffer.
     */
    public int capacity() {
        return capacity;
    }

    /**
     * Clears all content from the buffer.
     */
    public void clear() {
        rwLock.writeLock().lock();
        try {
            head = 0;
            size = 0;
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    private void appendInternal(byte b) {
        if (size < capacity) {
            int insertIdx = (head + size) % capacity;
            buffer[insertIdx] = b;
            size++;
        } else {
            buffer[head] = b;
            head = (head + 1) % capacity;
        }
    }
}
