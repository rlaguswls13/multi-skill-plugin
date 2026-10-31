package com.msp.httprange;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RangeHeaderParserTest {

    private static final long TOTAL_LENGTH = 1234;

    @Test
    @DisplayName("Explicit range: bytes=0-499")
    void testExplicitRange() {
        ByteRange range = RangeHeaderParser.parse("bytes=0-499", TOTAL_LENGTH);

        assertTrue(range.isSatisfiable());
        assertEquals(0, range.start());
        assertEquals(499, range.end());
        assertEquals(500, range.length());
        assertEquals(206, range.httpStatus());
        assertEquals("bytes 0-499/1234", range.toContentRangeHeader());
    }

    @Test
    @DisplayName("Open-ended range: bytes=500-")
    void testOpenEndedRange() {
        ByteRange range = RangeHeaderParser.parse("bytes=500-", TOTAL_LENGTH);

        assertTrue(range.isSatisfiable());
        assertEquals(500, range.start());
        assertEquals(1233, range.end());
        assertEquals(734, range.length());
        assertEquals(206, range.httpStatus());
        assertEquals("bytes 500-1233/1234", range.toContentRangeHeader());
    }

    @Test
    @DisplayName("Suffix range: bytes=-500 (last 500 bytes)")
    void testSuffixRange() {
        ByteRange range = RangeHeaderParser.parse("bytes=-500", TOTAL_LENGTH);

        assertTrue(range.isSatisfiable());
        assertEquals(734, range.start()); // 1234 - 500 = 734
        assertEquals(1233, range.end());
        assertEquals(500, range.length());
        assertEquals("bytes 734-1233/1234", range.toContentRangeHeader());
    }

    @Test
    @DisplayName("Suffix range larger than total size: bytes=-2000")
    void testSuffixRangeExceedingTotal() {
        ByteRange range = RangeHeaderParser.parse("bytes=-2000", TOTAL_LENGTH);

        assertTrue(range.isSatisfiable());
        assertEquals(0, range.start());
        assertEquals(1233, range.end());
        assertEquals(1234, range.length());
    }

    @Test
    @DisplayName("Range clamped when end exceeds total length: bytes=0-2000")
    void testEndClamped() {
        ByteRange range = RangeHeaderParser.parse("bytes=0-2000", TOTAL_LENGTH);

        assertTrue(range.isSatisfiable());
        assertEquals(0, range.start());
        assertEquals(1233, range.end());
        assertEquals(1234, range.length());
    }

    @Test
    @DisplayName("Unsatisfiable: start >= totalLength (bytes=1234-)")
    void testStartExceedsTotalLength() {
        ByteRange range = RangeHeaderParser.parse("bytes=1234-", TOTAL_LENGTH);

        assertFalse(range.isSatisfiable());
        assertEquals(416, range.httpStatus());
        assertEquals(0, range.length());
        assertEquals("bytes */1234", range.toContentRangeHeader());
    }

    @Test
    @DisplayName("Unsatisfiable: start > end (bytes=500-200)")
    void testStartGreaterThanEnd() {
        ByteRange range = RangeHeaderParser.parse("bytes=500-200", TOTAL_LENGTH);

        assertFalse(range.isSatisfiable());
        assertEquals(416, range.httpStatus());
        assertEquals("bytes */1234", range.toContentRangeHeader());
    }

    @Test
    @DisplayName("Unsatisfiable: empty or 0 total length")
    void testZeroTotalLength() {
        ByteRange range = RangeHeaderParser.parse("bytes=0-10", 0);

        assertFalse(range.isSatisfiable());
        assertEquals(416, range.httpStatus());
        assertEquals("bytes */0", range.toContentRangeHeader());
    }

    @Test
    @DisplayName("Malformed header formats")
    void testMalformedHeaders() {
        assertFalse(RangeHeaderParser.parse(null, TOTAL_LENGTH).isSatisfiable());
        assertFalse(RangeHeaderParser.parse("", TOTAL_LENGTH).isSatisfiable());
        assertFalse(RangeHeaderParser.parse("characters=0-10", TOTAL_LENGTH).isSatisfiable());
        assertFalse(RangeHeaderParser.parse("bytes=invalid", TOTAL_LENGTH).isSatisfiable());
        assertFalse(RangeHeaderParser.parse("bytes=-0", TOTAL_LENGTH).isSatisfiable());
    }

    @Test
    @DisplayName("Multiple ranges parsing")
    void testMultipleRanges() {
        List<ByteRange> ranges = RangeHeaderParser.parseMultiple("bytes=0-50, 100-200", TOTAL_LENGTH);

        assertEquals(2, ranges.size());
        assertEquals(0, ranges.get(0).start());
        assertEquals(50, ranges.get(0).end());
        assertEquals(100, ranges.get(1).start());
        assertEquals(200, ranges.get(1).end());
    }

    @Test
    @DisplayName("RangeChunkExtractor with byte array")
    void testExtractByteArray() {
        byte[] data = "0123456789ABCDEF".getBytes(StandardCharsets.UTF_8);
        ByteRange range = RangeHeaderParser.parse("bytes=2-6", data.length);

        byte[] chunk = RangeChunkExtractor.extract(data, range);
        assertEquals("23456", new String(chunk, StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("RangeChunkExtractor with InputStream")
    void testExtractInputStream() throws IOException {
        byte[] data = "Hello, World! HTTP 206 Partial Content Test".getBytes(StandardCharsets.UTF_8);
        ByteRange range = RangeHeaderParser.parse("bytes=7-11", data.length); // "World"

        try (InputStream in = new ByteArrayInputStream(data)) {
            byte[] chunk = RangeChunkExtractor.extract(in, range);
            assertEquals("World", new String(chunk, StandardCharsets.UTF_8));
        }
    }

    @Test
    @DisplayName("RangeChunkExtractor stream copy to OutputStream")
    void testStreamCopy() throws IOException {
        byte[] data = "Streamed Chunk Test Example".getBytes(StandardCharsets.UTF_8);
        ByteRange range = RangeHeaderParser.parse("bytes=9-13", data.length); // "Chunk"

        try (InputStream in = new ByteArrayInputStream(data);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            long transferred = RangeChunkExtractor.copy(in, out, range);
            assertEquals(5, transferred);
            assertEquals("Chunk", out.toString(StandardCharsets.UTF_8));
        }
    }

    @Test
    @DisplayName("RangeChunkExtractor rejects unsatisfiable range")
    void testExtractUnsatisfiableThrows() {
        byte[] data = new byte[100];
        ByteRange invalidRange = ByteRange.unsatisfiable(100);

        assertThrows(IllegalArgumentException.class, () -> RangeChunkExtractor.extract(data, invalidRange));
        assertThrows(IllegalArgumentException.class, () -> RangeChunkExtractor.extract(new ByteArrayInputStream(data), invalidRange));
    }
}
