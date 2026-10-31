package com.msp.ringbuffer;

import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * High-performance, zero-GC fixed-size circular byte ring buffer.
 * <p>
 * Designed for low-latency network I/O, streaming pipelines, and inter-thread buffer queues.
 * Thread-safe with intrinsic monitor synchronization.
 * </p>
 */
public final class ByteRingBuffer {

    private final byte[] buffer;
    private final int capacity;
    private int head; // read pointer
    private int tail; // write pointer
    private int size; // number of available bytes to read

    /**
     * Constructs a circular byte buffer with the specified capacity.
     *
     * @param capacity maximum buffer capacity in bytes
     * @throws IllegalArgumentException if capacity is less than or equal to 0
     */
    public ByteRingBuffer(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be positive: " + capacity);
        }
        this.capacity = capacity;
        this.buffer = new byte[capacity];
        this.head = 0;
        this.tail = 0;
        this.size = 0;
    }

    /**
     * Writes bytes from source array into this buffer.
     *
     * @param src    source byte array
     * @param offset starting index in source array
     * @param length number of bytes to write
     * @return number of bytes actually written (may be less than length if buffer has insufficient free space)
     */
    public synchronized int write(byte[] src, int offset, int length) {
        Objects.requireNonNull(src, "src must not be null");
        if (offset < 0 || length < 0 || offset + length > src.length) {
            throw new IndexOutOfBoundsException("Invalid offset=" + offset + ", length=" + length + ", src.length=" + src.length);
        }

        int toWrite = Math.min(length, availableWrite());
        if (toWrite <= 0) {
            return 0;
        }

        int firstChunk = Math.min(toWrite, capacity - tail);
        System.arraycopy(src, offset, buffer, tail, firstChunk);

        int secondChunk = toWrite - firstChunk;
        if (secondChunk > 0) {
            System.arraycopy(src, offset + firstChunk, buffer, 0, secondChunk);
        }

        tail = (tail + toWrite) % capacity;
        size += toWrite;
        return toWrite;
    }

    /**
     * Convenience method to write the entire source array.
     */
    public synchronized int write(byte[] src) {
        Objects.requireNonNull(src, "src must not be null");
        return write(src, 0, src.length);
    }

    /**
     * Writes a single byte into the buffer if space is available.
     *
     * @param b the byte to write
     * @return true if written successfully, false if the buffer is full
     */
    public synchronized boolean writeByte(byte b) {
        if (isFull()) {
            return false;
        }
        buffer[tail] = b;
        tail = (tail + 1) % capacity;
        size++;
        return true;
    }

    /**
     * Reads bytes from this buffer into destination array.
     *
     * @param dst    destination byte array
     * @param offset starting index in destination array
     * @param length maximum number of bytes to read
     * @return number of bytes actually read (may be less than length if available bytes are fewer)
     */
    public synchronized int read(byte[] dst, int offset, int length) {
        Objects.requireNonNull(dst, "dst must not be null");
        if (offset < 0 || length < 0 || offset + length > dst.length) {
            throw new IndexOutOfBoundsException("Invalid offset=" + offset + ", length=" + length + ", dst.length=" + dst.length);
        }

        int toRead = Math.min(length, availableRead());
        if (toRead <= 0) {
            return 0;
        }

        int firstChunk = Math.min(toRead, capacity - head);
        System.arraycopy(buffer, head, dst, offset, firstChunk);

        int secondChunk = toRead - firstChunk;
        if (secondChunk > 0) {
            System.arraycopy(buffer, 0, dst, offset + firstChunk, secondChunk);
        }

        head = (head + toRead) % capacity;
        size -= toRead;
        return toRead;
    }

    /**
     * Convenience method to read into the entire destination array.
     */
    public synchronized int read(byte[] dst) {
        Objects.requireNonNull(dst, "dst must not be null");
        return read(dst, 0, dst.length);
    }

    /**
     * Reads a single byte from the buffer.
     *
     * @return the byte read
     * @throws NoSuchElementException if the buffer is empty
     */
    public synchronized byte readByte() {
        if (isEmpty()) {
            throw new NoSuchElementException("Byte ring buffer is empty");
        }
        byte b = buffer[head];
        head = (head + 1) % capacity;
        size--;
        return b;
    }

    /**
     * Peeks at the next available byte without removing it.
     *
     * @return the next byte
     * @throws NoSuchElementException if the buffer is empty
     */
    public synchronized byte peek() {
        if (isEmpty()) {
            throw new NoSuchElementException("Byte ring buffer is empty");
        }
        return buffer[head];
    }

    /**
     * Copies bytes into dst without advancing the read position.
     */
    public synchronized int peek(byte[] dst, int offset, int length) {
        Objects.requireNonNull(dst, "dst must not be null");
        if (offset < 0 || length < 0 || offset + length > dst.length) {
            throw new IndexOutOfBoundsException();
        }

        int toPeek = Math.min(length, availableRead());
        if (toPeek <= 0) {
            return 0;
        }

        int firstChunk = Math.min(toPeek, capacity - head);
        System.arraycopy(buffer, head, dst, offset, firstChunk);

        int secondChunk = toPeek - firstChunk;
        if (secondChunk > 0) {
            System.arraycopy(buffer, 0, dst, offset + firstChunk, secondChunk);
        }

        return toPeek;
    }

    /**
     * Discards up to {@code bytesToSkip} bytes from the buffer.
     *
     * @param bytesToSkip number of bytes to discard
     * @return number of bytes actually skipped
     */
    public synchronized int skip(int bytesToSkip) {
        if (bytesToSkip <= 0) {
            return 0;
        }
        int toSkip = Math.min(bytesToSkip, size);
        head = (head + toSkip) % capacity;
        size -= toSkip;
        return toSkip;
    }

    /**
     * Returns the number of bytes currently stored and available to read.
     */
    public synchronized int availableRead() {
        return size;
    }

    /**
     * Returns the remaining capacity available to write before the buffer becomes full.
     */
    public synchronized int availableWrite() {
        return capacity - size;
    }

    /**
     * Returns the total fixed capacity of this buffer.
     */
    public int capacity() {
        return capacity;
    }

    /**
     * Returns true if the buffer is full.
     */
    public synchronized boolean isFull() {
        return size == capacity;
    }

    /**
     * Returns true if the buffer is empty.
     */
    public synchronized boolean isEmpty() {
        return size == 0;
    }

    /**
     * Clears all contents and resets read/write pointers to initial state.
     */
    public synchronized void clear() {
        head = 0;
        tail = 0;
        size = 0;
    }
}
