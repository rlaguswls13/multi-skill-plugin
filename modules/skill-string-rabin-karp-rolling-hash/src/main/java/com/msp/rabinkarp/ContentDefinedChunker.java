package com.msp.rabinkarp;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Content-Defined Chunking (CDC) boundary detector using a polynomial rolling hash.
 * <p>
 * Splits byte buffers at content-determined cut points where {@code (rollingHash.h1() & mask) == 0}
 * within {@code [minChunkSize, maxChunkSize]}, making chunk boundaries shift-invariant under insertions
 * and deletions (ideal for deduplication and delta sync).
 */
public final class ContentDefinedChunker {

    public record ChunkBoundary(int offset, int length, DoubleHash boundaryHash) {
        public int endOffset() {
            return offset + length;
        }
    }

    private final int minChunkSize;
    private final int averageChunkSize;
    private final int maxChunkSize;
    private final int windowSize;
    private final long mask;

    public ContentDefinedChunker(int minChunkSize, int averageChunkSize, int maxChunkSize, int windowSize) {
        if (windowSize <= 0) {
            throw new IllegalArgumentException("windowSize must be > 0");
        }
        if (minChunkSize < windowSize) {
            throw new IllegalArgumentException("minChunkSize must be >= windowSize");
        }
        if (averageChunkSize <= 0 || (averageChunkSize & (averageChunkSize - 1)) != 0) {
            throw new IllegalArgumentException("averageChunkSize must be a positive power of 2");
        }
        if (averageChunkSize < minChunkSize || maxChunkSize < averageChunkSize) {
            throw new IllegalArgumentException("Must satisfy minChunkSize <= averageChunkSize <= maxChunkSize");
        }
        this.minChunkSize = minChunkSize;
        this.averageChunkSize = averageChunkSize;
        this.maxChunkSize = maxChunkSize;
        this.windowSize = windowSize;
        this.mask = averageChunkSize - 1L;
    }

    public int minChunkSize() {
        return minChunkSize;
    }

    public int averageChunkSize() {
        return averageChunkSize;
    }

    public int maxChunkSize() {
        return maxChunkSize;
    }

    public int windowSize() {
        return windowSize;
    }

    /**
     * Partitions {@code data} into contiguous, non-overlapping content-defined chunks.
     */
    public List<ChunkBoundary> chunk(byte[] data) {
        Objects.requireNonNull(data, "data must not be null");
        int n = data.length;
        if (n == 0) {
            return List.of();
        }

        List<ChunkBoundary> chunks = new ArrayList<>();
        RollingHash rolling = new RollingHash(windowSize);
        int start = 0;

        while (start < n) {
            int remaining = n - start;
            if (remaining <= minChunkSize) {
                int hashStart = Math.max(start, n - windowSize);
                DoubleHash h = (n - hashStart == windowSize)
                        ? RollingHash.compute(data, hashStart, windowSize)
                        : RollingHash.compute(data, start, remaining);
                chunks.add(new ChunkBoundary(start, remaining, h));
                break;
            }

            int maxEnd = Math.min(n, start + maxChunkSize);
            int cutEnd = maxEnd;
            // First candidate cut position is start + minChunkSize (window ends at pos - 1)
            int winStart = start + minChunkSize - windowSize;
            DoubleHash current = rolling.init(data, winStart);

            int pos = start + minChunkSize;
            while (pos < maxEnd) {
                if ((current.h1() & mask) == 0L) {
                    cutEnd = pos;
                    break;
                }
                int outgoing = data[pos - windowSize] & 0xFF;
                int incoming = data[pos] & 0xFF;
                current = rolling.roll(outgoing, incoming);
                pos++;
            }

            chunks.add(new ChunkBoundary(start, cutEnd - start, current));
            start = cutEnd;
        }
        return chunks;
    }
}
