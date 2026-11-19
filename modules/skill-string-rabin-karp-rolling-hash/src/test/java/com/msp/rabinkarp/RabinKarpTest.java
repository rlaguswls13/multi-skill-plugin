package com.msp.rabinkarp;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class RabinKarpTest {

    @Test
    void rollingHashMatchesFreshComputationAcrossTextIncludingUnicode() {
        String text = "hello-세계-rolling-hash-🚀-test-12345";
        int window = 6;
        RollingHash rolling = new RollingHash(window);
        DoubleHash current = rolling.init(text, 0);
        assertEquals(RollingHash.compute(text, 0, window), current);

        for (int i = 1; i <= text.length() - window; i++) {
            current = rolling.roll(text.charAt(i - 1), text.charAt(i + window - 1));
            assertEquals(RollingHash.compute(text, i, window), current, "Mismatch at index " + i);
        }
    }

    @Test
    void singlePatternSearchFindsOverlappingAndBoundaryMatches() {
        assertEquals(List.of(0, 1, 2), RabinKarpSearcher.search("aaaaa", "aaa"));
        assertEquals(List.of(0, 7, 14), RabinKarpSearcher.search("abc1234abc5678abc", "abc"));
        assertEquals(7, RabinKarpSearcher.searchFirst("xyz1234abc5678", "abc"));
        assertEquals(-1, RabinKarpSearcher.searchFirst("abcdef", "zzz"));
        assertEquals(List.of(), RabinKarpSearcher.search("ab", "abcd"));

        assertThrows(IllegalArgumentException.class, () -> RabinKarpSearcher.search("abc", ""));
    }

    @Test
    void multiPatternEqualLengthSearchFindsAllPatternsInSinglePass() {
        String text = "the cat sat on the mat while the rat saw the cat";
        Map<String, List<Integer>> matches = RabinKarpSearcher.searchMulti(
                text, List.of("cat", "mat", "rat", "dog")
        );

        assertEquals(List.of(4, 45), matches.get("cat"));
        assertEquals(List.of(19), matches.get("mat"));
        assertEquals(List.of(33), matches.get("rat"));
        assertEquals(List.of(), matches.get("dog"));

        assertThrows(IllegalArgumentException.class,
                () -> RabinKarpSearcher.searchMulti(text, List.of("cat", "bird")));
    }

    @Test
    void winnowingGuaranteesSharedFingerprintForLongSharedSubstring() {
        int k = 5;
        int w = 4;
        // Guarantee threshold: k + w - 1 = 8 characters
        String shared = "UNIQUE_SHARED_BLOCK_2026";
        String docA = "prefix-alpha-12345-" + shared + "-suffix-A";
        String docB = "totally-different-head-" + shared + "-tail-B-999";

        WinnowingFingerprinter fp = new WinnowingFingerprinter(k, w);
        Set<DoubleHash> setA = fp.fingerprint(docA).stream()
                .map(WinnowingFingerprinter.Fingerprint::hash)
                .collect(Collectors.toSet());
        Set<DoubleHash> setB = fp.fingerprint(docB).stream()
                .map(WinnowingFingerprinter.Fingerprint::hash)
                .collect(Collectors.toSet());

        Set<DoubleHash> intersection = new HashSet<>(setA);
        intersection.retainAll(setB);
        assertFalse(intersection.isEmpty(), "Winnowing must share at least one fingerprint for common substring");
    }

    @Test
    void contentDefinedChunkerCoversBufferAndPreservesDownstreamBoundariesOnInsertion() {
        Random rnd = new Random(42L);
        byte[] original = new byte[4096];
        rnd.nextBytes(original);

        ContentDefinedChunker chunker = new ContentDefinedChunker(64, 256, 1024, 16);
        List<ContentDefinedChunker.ChunkBoundary> origChunks = chunker.chunk(original);

        assertFalse(origChunks.isEmpty());
        int cursor = 0;
        for (int i = 0; i < origChunks.size(); i++) {
            ContentDefinedChunker.ChunkBoundary c = origChunks.get(i);
            assertEquals(cursor, c.offset());
            if (i < origChunks.size() - 1) {
                assertTrue(c.length() >= 64 && c.length() <= 1024, "Chunk size out of bounds: " + c.length());
            } else {
                assertTrue(c.length() > 0 && c.length() <= 1024);
            }
            cursor = c.endOffset();
        }
        assertEquals(original.length, cursor, "Chunks must cover entire input contiguously");

        // Insert 37 bytes at the beginning and verify downstream chunks re-synchronize
        int insertLen = 37;
        byte[] shifted = new byte[insertLen + original.length];
        for (int i = 0; i < insertLen; i++) {
            shifted[i] = (byte) (i + 1);
        }
        System.arraycopy(original, 0, shifted, insertLen, original.length);

        List<ContentDefinedChunker.ChunkBoundary> shiftedChunks = chunker.chunk(shifted);
        Set<Integer> origEndOffsets = origChunks.stream()
                .map(ContentDefinedChunker.ChunkBoundary::endOffset)
                .collect(Collectors.toSet());

        long resynchronizedBoundaries = shiftedChunks.stream()
                .map(c -> c.endOffset() - insertLen)
                .filter(origEndOffsets::contains)
                .count();

        assertTrue(resynchronizedBoundaries >= 2,
                "Expected downstream CDC boundaries to re-synchronize after prefix insertion, got: "
                        + resynchronizedBoundaries);
    }
}
