package com.msp.ringbuffer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.NoSuchElementException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class ByteRingBufferTest {

    @Test
    @DisplayName("Single byte write and read operations")
    void testSingleByteOperations() {
        ByteRingBuffer ring = new ByteRingBuffer(3);

        assertTrue(ring.isEmpty());
        assertFalse(ring.isFull());
        assertEquals(3, ring.availableWrite());
        assertEquals(0, ring.availableRead());

        assertTrue(ring.writeByte((byte) 10));
        assertTrue(ring.writeByte((byte) 20));
        assertTrue(ring.writeByte((byte) 30));

        assertTrue(ring.isFull());
        assertFalse(ring.writeByte((byte) 40)); // Full

        assertEquals((byte) 10, ring.peek());
        assertEquals((byte) 10, ring.readByte());
        assertEquals((byte) 20, ring.readByte());
        assertEquals((byte) 30, ring.readByte());

        assertTrue(ring.isEmpty());
        assertThrows(NoSuchElementException.class, ring::readByte);
        assertThrows(NoSuchElementException.class, ring::peek);
    }

    @Test
    @DisplayName("Circular wrap-around array write and read")
    void testCircularWrapAround() {
        ByteRingBuffer ring = new ByteRingBuffer(5);

        // Write 3 bytes
        byte[] src1 = {1, 2, 3};
        assertEquals(3, ring.write(src1));

        // Read 2 bytes -> head is now at index 2
        byte[] dst1 = new byte[2];
        assertEquals(2, ring.read(dst1));
        assertArrayEquals(new byte[]{1, 2}, dst1);
        assertEquals(1, ring.availableRead());
        assertEquals(4, ring.availableWrite());

        // Write 4 bytes -> tail wraps around end of buffer (indices 3, 4, 0, 1)
        byte[] src2 = {4, 5, 6, 7};
        assertEquals(4, ring.write(src2));
        assertTrue(ring.isFull());

        // Read all 5 bytes across wrap-around boundary (head 2 -> 3, 4, 0, 1)
        byte[] dst2 = new byte[5];
        assertEquals(5, ring.read(dst2));
        assertArrayEquals(new byte[]{3, 4, 5, 6, 7}, dst2);
        assertTrue(ring.isEmpty());
    }

    @Test
    @DisplayName("Partial write and partial read based on available space")
    void testPartialWriteAndRead() {
        ByteRingBuffer ring = new ByteRingBuffer(4);

        byte[] input = {10, 20, 30, 40, 50, 60};
        int written = ring.write(input);
        assertEquals(4, written); // Only 4 bytes fit

        byte[] out = new byte[10];
        int read = ring.read(out);
        assertEquals(4, read); // Only 4 bytes available
        assertArrayEquals(new byte[]{10, 20, 30, 40}, new byte[]{out[0], out[1], out[2], out[3]});
    }

    @Test
    @DisplayName("Bulk peek and skip")
    void testPeekAndSkip() {
        ByteRingBuffer ring = new ByteRingBuffer(10);
        ring.write(new byte[]{10, 20, 30, 40, 50});

        byte[] peekBuf = new byte[3];
        int peeked = ring.peek(peekBuf, 0, 3);
        assertEquals(3, peeked);
        assertArrayEquals(new byte[]{10, 20, 30}, peekBuf);
        assertEquals(5, ring.availableRead()); // Peek must not consume

        int skipped = ring.skip(2); // Skip 10, 20
        assertEquals(2, skipped);
        assertEquals(3, ring.availableRead());

        byte[] remaining = new byte[3];
        ring.read(remaining);
        assertArrayEquals(new byte[]{30, 40, 50}, remaining);
    }

    @Test
    @DisplayName("Clear resets buffer state")
    void testClear() {
        ByteRingBuffer ring = new ByteRingBuffer(10);
        ring.write(new byte[]{1, 2, 3, 4});
        assertFalse(ring.isEmpty());

        ring.clear();
        assertTrue(ring.isEmpty());
        assertFalse(ring.isFull());
        assertEquals(0, ring.availableRead());
        assertEquals(10, ring.availableWrite());
    }

    @Test
    @DisplayName("Concurrent producer-consumer data integrity")
    void testConcurrentProducerConsumer() throws InterruptedException {
        int capacity = 128;
        int totalItems = 50_000;
        ByteRingBuffer ring = new ByteRingBuffer(capacity);

        CountDownLatch latch = new CountDownLatch(2);
        AtomicBoolean success = new AtomicBoolean(true);

        // Producer
        Thread producer = new Thread(() -> {
            try {
                for (int i = 0; i < totalItems; i++) {
                    byte val = (byte) (i & 0xFF);
                    while (!ring.writeByte(val)) {
                        Thread.onSpinWait();
                    }
                }
            } finally {
                latch.countDown();
            }
        });

        // Consumer
        Thread consumer = new Thread(() -> {
            try {
                for (int i = 0; i < totalItems; i++) {
                    byte expected = (byte) (i & 0xFF);
                    while (ring.isEmpty()) {
                        Thread.onSpinWait();
                    }
                    byte actual = ring.readByte();
                    if (actual != expected) {
                        success.set(false);
                        break;
                    }
                }
            } finally {
                latch.countDown();
            }
        });

        producer.start();
        consumer.start();
        latch.await();

        assertTrue(success.get(), "All bytes must be transferred in exact FIFO order without corruption");
        assertTrue(ring.isEmpty());
    }

    @Test
    @DisplayName("Invalid arguments and boundary conditions")
    void testInvalidArguments() {
        assertThrows(IllegalArgumentException.class, () -> new ByteRingBuffer(0));
        assertThrows(IllegalArgumentException.class, () -> new ByteRingBuffer(-5));

        ByteRingBuffer ring = new ByteRingBuffer(10);
        assertThrows(NullPointerException.class, () -> ring.write(null));
        assertThrows(NullPointerException.class, () -> ring.read(null));
        assertThrows(IndexOutOfBoundsException.class, () -> ring.write(new byte[5], -1, 2));
        assertThrows(IndexOutOfBoundsException.class, () -> ring.write(new byte[5], 2, 10));
        assertThrows(IndexOutOfBoundsException.class, () -> ring.read(new byte[5], 0, 10));
    }
}
