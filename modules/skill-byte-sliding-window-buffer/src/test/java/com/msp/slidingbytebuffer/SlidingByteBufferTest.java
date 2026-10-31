package com.msp.slidingbytebuffer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SlidingByteBuffer Tests")
class SlidingByteBufferTest {

    @Test
    @DisplayName("Validation of capacity argument")
    void testCapacityValidation() {
        assertThrows(IllegalArgumentException.class, () -> new SlidingByteBuffer(0));
        assertThrows(IllegalArgumentException.class, () -> new SlidingByteBuffer(-5));
        SlidingByteBuffer buffer = new SlidingByteBuffer(16);
        assertEquals(16, buffer.capacity());
        assertEquals(0, buffer.size());
    }

    @Test
    @DisplayName("FIFO sliding when capacity is exceeded")
    void testFifoSliding() {
        SlidingByteBuffer buffer = new SlidingByteBuffer(4);
        buffer.append((byte) 'A');
        buffer.append((byte) 'B');
        buffer.append((byte) 'C');
        buffer.append((byte) 'D');
        assertEquals(4, buffer.size());
        assertArrayEquals(new byte[]{'A', 'B', 'C', 'D'}, buffer.toByteArray());

        // Append 5th byte, 'A' should slide out
        buffer.append((byte) 'E');
        assertEquals(4, buffer.size());
        assertArrayEquals(new byte[]{'B', 'C', 'D', 'E'}, buffer.toByteArray());

        // Append 6th byte, 'B' slides out
        buffer.append((byte) 'F');
        assertEquals(4, buffer.size());
        assertArrayEquals(new byte[]{'C', 'D', 'E', 'F'}, buffer.toByteArray());
    }

    @Test
    @DisplayName("Multi-byte append and overflow retention")
    void testMultiByteAppend() {
        SlidingByteBuffer buffer = new SlidingByteBuffer(5);
        byte[] input = "0123456789".getBytes(StandardCharsets.US_ASCII);

        buffer.append(input, 0, 3); // "012"
        assertEquals(3, buffer.size());
        assertArrayEquals("012".getBytes(StandardCharsets.US_ASCII), buffer.toByteArray());

        // Append remaining 7 bytes (total 10 bytes appended into size 5 buffer)
        buffer.append(input, 3, 7);
        assertEquals(5, buffer.size());
        // Last 5 bytes should be "56789"
        assertArrayEquals("56789".getBytes(StandardCharsets.US_ASCII), buffer.toByteArray());
    }

    @Test
    @DisplayName("Appending array larger than capacity retains only trailing bytes")
    void testAppendLargerThanCapacity() {
        SlidingByteBuffer buffer = new SlidingByteBuffer(4);
        byte[] large = "ABCDEFGHIJK".getBytes(StandardCharsets.US_ASCII);

        buffer.append(large);
        assertEquals(4, buffer.size());
        assertArrayEquals("HIJK".getBytes(StandardCharsets.US_ASCII), buffer.toByteArray());
    }

    @Test
    @DisplayName("matchesTail detects HTTP header terminator \\r\\n\\r\\n")
    void testMatchesTailHttpHeader() {
        SlidingByteBuffer buffer = new SlidingByteBuffer(64);
        byte[] crlf2 = "\r\n\r\n".getBytes(StandardCharsets.US_ASCII);

        assertFalse(buffer.matchesTail(crlf2));

        buffer.append("GET / HTTP/1.1\r\nHost: example.com\r\n".getBytes(StandardCharsets.US_ASCII));
        assertFalse(buffer.matchesTail(crlf2));

        buffer.append("\r\n".getBytes(StandardCharsets.US_ASCII));
        assertTrue(buffer.matchesTail(crlf2));

        // Adding more characters should break the tail match
        buffer.append((byte) 'X');
        assertFalse(buffer.matchesTail(crlf2));
    }

    @Test
    @DisplayName("indexOf pattern searching including wrap-around")
    void testIndexOf() {
        SlidingByteBuffer buffer = new SlidingByteBuffer(6);
        buffer.append("123456".getBytes(StandardCharsets.US_ASCII));

        assertEquals(2, buffer.indexOf("34".getBytes(StandardCharsets.US_ASCII)));
        assertEquals(-1, buffer.indexOf("99".getBytes(StandardCharsets.US_ASCII)));

        // Slide two bytes: now buffer holds "345678" wrapped in circular array
        buffer.append((byte) '7');
        buffer.append((byte) '8');

        assertEquals(0, buffer.indexOf("34".getBytes(StandardCharsets.US_ASCII)));
        assertEquals(4, buffer.indexOf("78".getBytes(StandardCharsets.US_ASCII)));
        assertEquals(2, buffer.indexOf("567".getBytes(StandardCharsets.US_ASCII)));
    }

    @Test
    @DisplayName("Clear resets size and contents")
    void testClear() {
        SlidingByteBuffer buffer = new SlidingByteBuffer(8);
        buffer.append("abcdef".getBytes(StandardCharsets.US_ASCII));
        assertEquals(6, buffer.size());

        buffer.clear();
        assertEquals(0, buffer.size());
        assertEquals(0, buffer.toByteArray().length);
        assertFalse(buffer.matchesTail("a".getBytes(StandardCharsets.US_ASCII)));
    }

    @Test
    @DisplayName("Multithreaded concurrent appends and reads")
    void testConcurrentAccess() throws InterruptedException {
        SlidingByteBuffer buffer = new SlidingByteBuffer(1024);
        int threadCount = 6;
        int appendsPerThread = 200;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final byte b = (byte) (i + 1);
            executor.submit(() -> {
                try {
                    for (int j = 0; j < appendsPerThread; j++) {
                        buffer.append(b);
                        buffer.matchesTail(new byte[]{b});
                        buffer.size();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        executor.shutdown();
        assertTrue(buffer.size() > 0);
    }
}
