package com.msp.gzipstream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class GzipStreamerTest {

    @Test
    @DisplayName("GZIP magic header detection test")
    void testIsGzipMagicNumber() throws IOException {
        assertFalse(GzipStreamer.isGzip((byte[]) null));
        assertFalse(GzipStreamer.isGzip(new byte[0]));
        assertFalse(GzipStreamer.isGzip(new byte[]{0x1F}));
        assertFalse(GzipStreamer.isGzip(new byte[]{0x1F, 0x00}));
        assertFalse(GzipStreamer.isGzip(new byte[]{(byte) 0x8B, 0x1F}));

        byte[] validHeader = new byte[]{(byte) 0x1F, (byte) 0x8B, 0x08, 0x00};
        assertTrue(GzipStreamer.isGzip(validHeader));

        // Test with BufferedInputStream
        BufferedInputStream streamWithGzip = new BufferedInputStream(new ByteArrayInputStream(validHeader));
        assertTrue(GzipStreamer.isGzip(streamWithGzip));
        // Verify stream position was reset
        assertEquals(0x1F, streamWithGzip.read());

        BufferedInputStream streamNonGzip = new BufferedInputStream(new ByteArrayInputStream(new byte[]{1, 2}));
        assertFalse(GzipStreamer.isGzip(streamNonGzip));
        assertFalse(GzipStreamer.isGzip((BufferedInputStream) null));
    }

    @Test
    @DisplayName("GZIP byte compression and decompression roundtrip")
    void testGzipRoundTrip() {
        String originalText = "Hello, GzipStreamer! Testing high-performance compression with pure Java 21 standard library.";
        byte[] original = originalText.getBytes(StandardCharsets.UTF_8);

        byte[] compressed = GzipStreamer.compressGzip(original);
        assertNotNull(compressed);
        assertTrue(compressed.length > 0);
        assertTrue(GzipStreamer.isGzip(compressed));

        byte[] decompressed = GzipStreamer.decompressGzip(compressed);
        assertArrayEquals(original, decompressed);
        assertEquals(originalText, new String(decompressed, StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("GZIP empty byte array roundtrip")
    void testGzipEmptyByteArray() {
        byte[] empty = new byte[0];
        byte[] compressed = GzipStreamer.compressGzip(empty);
        assertTrue(compressed.length > 0);
        assertTrue(GzipStreamer.isGzip(compressed));

        byte[] decompressed = GzipStreamer.decompressGzip(compressed);
        assertEquals(0, decompressed.length);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 1, 6, 9})
    @DisplayName("DEFLATE compression with various levels roundtrip")
    void testDeflateRoundTripLevels(int level) {
        String originalText = "The quick brown fox jumps over the lazy dog repeatedly. ".repeat(50);
        byte[] original = originalText.getBytes(StandardCharsets.UTF_8);

        byte[] compressed = GzipStreamer.compressDeflate(original, level);
        assertNotNull(compressed);

        byte[] decompressed = GzipStreamer.decompressDeflate(compressed);
        assertArrayEquals(original, decompressed);
    }

    @Test
    @DisplayName("DEFLATE default compression overload roundtrip")
    void testDeflateDefaultRoundTrip() {
        byte[] data = "Short message for deflate default level.".getBytes(StandardCharsets.UTF_8);
        byte[] compressed = GzipStreamer.compressDeflate(data);
        byte[] decompressed = GzipStreamer.decompressDeflate(compressed);
        assertArrayEquals(data, decompressed);
    }

    @ParameterizedTest
    @EnumSource(CompressionType.class)
    @DisplayName("Streaming compression and decompression roundtrip")
    void testStreamingRoundTrip(CompressionType type) {
        byte[] payload = new byte[64 * 1024]; // 64 KB pseudo random repeating data
        new Random(42).nextBytes(payload);

        ByteArrayInputStream source = new ByteArrayInputStream(payload);
        ByteArrayOutputStream compressedOut = new ByteArrayOutputStream();

        long uncompressedRead = GzipStreamer.compressStream(source, compressedOut, type);
        assertEquals(payload.length, uncompressedRead);

        byte[] compressedBytes = compressedOut.toByteArray();
        assertTrue(compressedBytes.length > 0);

        ByteArrayInputStream compressedIn = new ByteArrayInputStream(compressedBytes);
        ByteArrayOutputStream decompressedOut = new ByteArrayOutputStream();

        long decompressedWritten = GzipStreamer.decompressStream(compressedIn, decompressedOut, type);
        assertEquals(payload.length, decompressedWritten);
        assertArrayEquals(payload, decompressedOut.toByteArray());
    }

    @Test
    @DisplayName("Decompress invalid or corrupted GZIP data throws CompressionException")
    void testCorruptedGzipThrowsException() {
        byte[] notGzip = "plain text without gzip magic header".getBytes(StandardCharsets.UTF_8);
        assertThrows(CompressionException.class, () -> GzipStreamer.decompressGzip(notGzip));

        byte[] badGzip = new byte[]{(byte) 0x1F, (byte) 0x8B, 0x00, 0x00};
        assertThrows(CompressionException.class, () -> GzipStreamer.decompressGzip(badGzip));
    }

    @Test
    @DisplayName("Decompress invalid DEFLATE data throws CompressionException")
    void testCorruptedDeflateThrowsException() {
        byte[] corrupt = new byte[]{1, 2, 3, 4, 5, 6, 7, 8};
        assertThrows(CompressionException.class, () -> GzipStreamer.decompressDeflate(corrupt));
    }

    @Test
    @DisplayName("Invalid arguments validation")
    void testInvalidArguments() {
        assertThrows(NullPointerException.class, () -> GzipStreamer.compressGzip(null));
        assertThrows(NullPointerException.class, () -> GzipStreamer.decompressGzip(null));
        assertThrows(NullPointerException.class, () -> GzipStreamer.compressDeflate(null));
        assertThrows(NullPointerException.class, () -> GzipStreamer.decompressDeflate(null));

        assertThrows(IllegalArgumentException.class, () -> GzipStreamer.compressDeflate(new byte[10], -2));
        assertThrows(IllegalArgumentException.class, () -> GzipStreamer.compressDeflate(new byte[10], 10));

        ByteArrayInputStream in = new ByteArrayInputStream(new byte[10]);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        assertThrows(NullPointerException.class, () -> GzipStreamer.compressStream(null, out, CompressionType.GZIP));
        assertThrows(NullPointerException.class, () -> GzipStreamer.compressStream(in, null, CompressionType.GZIP));
        assertThrows(NullPointerException.class, () -> GzipStreamer.compressStream(in, out, null));

        assertThrows(NullPointerException.class, () -> GzipStreamer.decompressStream(null, out, CompressionType.GZIP));
        assertThrows(NullPointerException.class, () -> GzipStreamer.decompressStream(in, null, CompressionType.GZIP));
        assertThrows(NullPointerException.class, () -> GzipStreamer.decompressStream(in, out, null));
    }
}
