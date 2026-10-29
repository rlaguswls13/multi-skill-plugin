package com.msp.filesplit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileSplitterMergerTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Split 1MB file into 256KB chunks, verify 4 parts created, merge back and assert SHA-256 matches")
    void testSplitAndMergeOneMegabyte() throws IOException {
        Path source = tempDir.resolve("original_1mb.bin");
        int totalSize = 1024 * 1024; // 1 MB
        byte[] data = new byte[totalSize];
        new Random(42).nextBytes(data);
        Files.write(source, data);

        String originalSha256 = FileMerger.computeSha256(source);

        Path chunksDir = tempDir.resolve("chunks_1mb");
        long chunkSize = 256 * 1024; // 256 KB
        List<Path> chunks = FileSplitter.splitBySize(source, chunkSize, chunksDir);

        assertEquals(4, chunks.size(), "1MB file split into 256KB chunks must yield exactly 4 parts");
        for (int i = 0; i < 4; i++) {
            assertEquals(chunkSize, Files.size(chunks.get(i)), "Each chunk must be exactly 256KB");
            assertTrue(chunks.get(i).getFileName().toString().endsWith(String.format(".part%04d", i + 1)));
        }

        Path merged = tempDir.resolve("merged_1mb.bin");
        FileMerger.merge(chunks, merged, originalSha256);

        assertEquals(totalSize, Files.size(merged));
        String mergedSha256 = FileMerger.computeSha256(merged);
        assertEquals(originalSha256, mergedSha256);
        assertArrayEquals(data, Files.readAllBytes(merged));
    }

    @Test
    @DisplayName("Split file with uneven size creates proper remainder chunk and merges correctly")
    void testUnevenSplitAndMerge() throws IOException {
        Path source = tempDir.resolve("uneven.bin");
        int totalSize = 700 * 1024; // 700 KB
        byte[] data = new byte[totalSize];
        new Random(1234).nextBytes(data);
        Files.write(source, data);

        String originalSha = FileMerger.computeSha256(source);

        Path chunksDir = tempDir.resolve("chunks_uneven");
        long chunkSize = 256 * 1024; // 256 KB
        List<Path> chunks = FileSplitter.splitBySize(source, chunkSize, chunksDir);

        assertEquals(3, chunks.size(), "700KB split by 256KB should yield 3 chunks (256KB, 256KB, 188KB)");
        assertEquals(256 * 1024, Files.size(chunks.get(0)));
        assertEquals(256 * 1024, Files.size(chunks.get(1)));
        assertEquals(188 * 1024, Files.size(chunks.get(2)));

        Path merged = tempDir.resolve("merged_uneven.bin");
        FileMerger.merge(chunks, merged);
        assertEquals(originalSha, FileMerger.computeSha256(merged));
    }

    @Test
    @DisplayName("Split by lines partitions text file accurately")
    void testSplitByLines() throws IOException {
        Path source = tempDir.resolve("data.txt");
        List<String> lines = List.of(
                "Line 1", "Line 2", "Line 3",
                "Line 4", "Line 5", "Line 6",
                "Line 7", "Line 8", "Line 9",
                "Line 10"
        );
        Files.write(source, lines, StandardCharsets.UTF_8);

        Path chunksDir = tempDir.resolve("chunks_lines");
        List<Path> chunks = FileSplitter.splitByLines(source, 3, chunksDir);

        assertEquals(4, chunks.size(), "10 lines split by 3 lines per chunk yields 4 chunks (3, 3, 3, 1)");
        assertEquals(3, Files.readAllLines(chunks.get(0)).size());
        assertEquals(3, Files.readAllLines(chunks.get(1)).size());
        assertEquals(3, Files.readAllLines(chunks.get(2)).size());
        assertEquals(1, Files.readAllLines(chunks.get(3)).size());
        assertEquals("Line 10", Files.readAllLines(chunks.get(3)).getFirst());
    }

    @Test
    @DisplayName("Merge with invalid expected SHA-256 throws IllegalStateException")
    void testChecksumMismatchThrowsException() throws IOException {
        Path source = tempDir.resolve("small.txt");
        Files.writeString(source, "Payload");
        List<Path> chunks = FileSplitter.splitBySize(source, 1024, tempDir.resolve("small_chunks"));

        Path dest = tempDir.resolve("small_merged.txt");
        assertThrows(IllegalStateException.class, () ->
                FileMerger.merge(chunks, dest, "0000000000000000000000000000000000000000000000000000000000000000")
        );
    }

    @Test
    @DisplayName("Invalid parameters and missing files throw appropriate exceptions")
    void testErrorHandling() {
        Path missing = tempDir.resolve("does_not_exist.txt");
        assertThrows(NoSuchFileException.class, () ->
                FileSplitter.splitBySize(missing, 1024, tempDir.resolve("out"))
        );
        assertThrows(IllegalArgumentException.class, () ->
                FileSplitter.splitBySize(tempDir, 1024, tempDir.resolve("out"))
        );
        assertThrows(IllegalArgumentException.class, () ->
                FileSplitter.splitBySize(tempDir.resolve("file.txt"), -1, tempDir.resolve("out"))
        );

        assertThrows(NoSuchFileException.class, () ->
                FileMerger.merge(List.of(missing), tempDir.resolve("out.bin"))
        );
    }
}
