package com.msp.kmp;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class KmpAutomatonTest {

    @Test
    void computesCorrectLpsFailureTable() {
        KmpAutomaton kmp = KmpAutomaton.compile("ABABCABAB");
        assertArrayEquals(new int[]{0, 0, 1, 2, 0, 1, 2, 3, 4}, kmp.lpsTable());

        KmpAutomaton kmp2 = KmpAutomaton.compile("AAACAAAA");
        assertArrayEquals(new int[]{0, 1, 2, 0, 1, 2, 3, 3}, kmp2.lpsTable());
    }

    @Test
    void findsOverlappingAndNonOverlappingMatchesInCharSequenceAndCharArray() {
        KmpAutomaton kmp = KmpAutomaton.compile("ABA");
        String text = "ABABABA";

        // First occurrence
        assertEquals(0, kmp.indexOf(text));
        assertEquals(2, kmp.indexOf(text, 1));
        assertEquals(4, kmp.indexOf(text.toCharArray(), 3));
        assertEquals(-1, kmp.indexOf("AB"));

        // Overlapping matches: indices 0, 2, 4
        assertEquals(List.of(0, 2, 4), kmp.findAll(text, true));
        assertEquals(List.of(0, 2, 4), kmp.findAll(text.toCharArray(), true));
        assertEquals(3, kmp.countMatches(text, true));

        // Non-overlapping matches: indices 0, 4
        assertEquals(List.of(0, 4), kmp.findAll(text, false));
        assertEquals(List.of(0, 4), kmp.findAll(text.toCharArray(), false));
        assertEquals(2, kmp.countMatches(text, false));
    }

    @Test
    void searchesInByteArraysAndStreamingInputStream() throws Exception {
        byte[] pattern = new byte[]{0x0A, 0x0B, 0x0A};
        byte[] payload = new byte[]{0x00, 0x0A, 0x0B, 0x0A, 0x0B, 0x0A, 0x7F};

        KmpAutomaton kmp = KmpAutomaton.compile(pattern);

        assertEquals(1, kmp.indexOf(payload));
        assertEquals(List.of(1, 3), kmp.findAll(payload, true));
        assertEquals(List.of(1), kmp.findAll(payload, false));

        assertEquals(1L, kmp.indexOf(new ByteArrayInputStream(payload)));
        assertEquals(2L, kmp.countMatches(new ByteArrayInputStream(payload), true));
        assertArrayEquals(new long[]{1L, 3L}, kmp.streamMatches(new ByteArrayInputStream(payload), true).toArray());
    }

    @Test
    void streamsMatchesAcrossBufferChunkBoundariesWithoutBacktracking() throws Exception {
        // Build a large stream (> 12,000 chars, spanning multiple 4096-char internal chunks)
        // with a match straddling the exact 4096 boundary!
        String pattern = "NEEDLE_IN_STREAM";
        KmpAutomaton kmp = KmpAutomaton.compile(pattern);

        StringBuilder sb = new StringBuilder(15000);
        sb.append("x".repeat(100));
        sb.append(pattern); // match at index 100
        while (sb.length() < 4090) {
            sb.append('y');
        }
        // At index 4090, append pattern so it crosses index 4096 chunk boundary (4090..4106)
        sb.append(pattern); // match at index 4090
        sb.append("z".repeat(5000));
        int thirdMatchIdx = sb.length();
        sb.append(pattern); // match at thirdMatchIdx

        String largeContent = sb.toString();

        long[] streamedPositions = kmp.streamMatches(new StringReader(largeContent)).toArray();
        assertArrayEquals(new long[]{100L, 4090L, thirdMatchIdx}, streamedPositions);
        assertEquals(100L, kmp.indexOf(new StringReader(largeContent)));
        assertEquals(3L, kmp.countMatches(new StringReader(largeContent), true));

        // Also verify byte stream across 4096-byte boundary
        byte[] utf8Bytes = largeContent.getBytes(StandardCharsets.UTF_8);
        long[] byteStreamPositions = kmp.streamMatches(new ByteArrayInputStream(utf8Bytes)).toArray();
        assertArrayEquals(new long[]{100L, 4090L, thirdMatchIdx}, byteStreamPositions);
    }

    @Test
    void validatesEmptyAndNullPatterns() {
        assertThrows(NullPointerException.class, () -> KmpAutomaton.compile((CharSequence) null));
        assertThrows(IllegalArgumentException.class, () -> KmpAutomaton.compile(""));
        assertThrows(IllegalArgumentException.class, () -> KmpAutomaton.compile(new char[0]));
        assertThrows(IllegalArgumentException.class, () -> KmpAutomaton.compile(new byte[0]));
    }
}
