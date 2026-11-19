package com.msp.kmp;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.LongConsumer;
import java.util.stream.LongStream;
import java.util.stream.StreamSupport;

/**
 * Knuth-Morris-Pratt (KMP) Failure-Function Automaton for Zero-Backtracking O(N) Streaming Pattern Search.
 * <p>
 * Features:
 * <ul>
 *     <li>Precomputes the LPS (Longest Proper Prefix which is also a Suffix) failure table in {@code O(M)} time.</li>
 *     <li>Zero-backtracking {@code O(N)} text search over {@link CharSequence}, {@code char[]}, {@code byte[]},
 *         streaming {@link Reader}, and streaming {@link InputStream} without buffering the entire stream in memory.</li>
 *     <li>Supports {@code indexOf}, {@code findAll} (both overlapping and non-overlapping), {@code countMatches},
 *         and lazy {@code streamMatches(Reader)} / {@code streamMatches(InputStream)}.</li>
 * </ul>
 */
public final class KmpAutomaton {

    private static final int STREAM_BUFFER_SIZE = 4096;

    private final char[] charPattern;
    private final byte[] bytePattern;
    private final int[] charLps;
    private final int[] byteLps;

    private KmpAutomaton(char[] charPattern, byte[] bytePattern, int[] charLps, int[] byteLps) {
        this.charPattern = charPattern;
        this.bytePattern = bytePattern;
        this.charLps = charLps;
        this.byteLps = byteLps;
    }

    /**
     * Compiles a character/String pattern into an immutable, thread-safe {@link KmpAutomaton}.
     */
    public static KmpAutomaton compile(CharSequence pattern) {
        Objects.requireNonNull(pattern, "pattern must not be null");
        if (pattern.isEmpty()) {
            throw new IllegalArgumentException("KMP pattern must not be empty");
        }
        int len = pattern.length();
        char[] chars = new char[len];
        for (int i = 0; i < len; i++) {
            chars[i] = pattern.charAt(i);
        }
        byte[] utf8Bytes = pattern.toString().getBytes(StandardCharsets.UTF_8);
        return new KmpAutomaton(chars, utf8Bytes, computeCharLps(chars), computeByteLps(utf8Bytes));
    }

    /**
     * Compiles a character array pattern into an immutable, thread-safe {@link KmpAutomaton}.
     */
    public static KmpAutomaton compile(char[] pattern) {
        Objects.requireNonNull(pattern, "pattern must not be null");
        if (pattern.length == 0) {
            throw new IllegalArgumentException("KMP pattern must not be empty");
        }
        char[] copy = Arrays.copyOf(pattern, pattern.length);
        byte[] utf8Bytes = new String(copy).getBytes(StandardCharsets.UTF_8);
        return new KmpAutomaton(copy, utf8Bytes, computeCharLps(copy), computeByteLps(utf8Bytes));
    }

    /**
     * Compiles a raw byte array pattern into an immutable, thread-safe {@link KmpAutomaton}.
     */
    public static KmpAutomaton compile(byte[] pattern) {
        Objects.requireNonNull(pattern, "pattern must not be null");
        if (pattern.length == 0) {
            throw new IllegalArgumentException("KMP byte pattern must not be empty");
        }
        byte[] copy = Arrays.copyOf(pattern, pattern.length);
        char[] chars = new char[copy.length];
        for (int i = 0; i < copy.length; i++) {
            chars[i] = (char) (copy[i] & 0xFF);
        }
        int[] lps = computeByteLps(copy);
        return new KmpAutomaton(chars, copy, lps, lps);
    }

    /**
     * Computes the LPS (Longest Proper Prefix which is also Suffix) failure table for a character pattern in O(M) time.
     */
    public static int[] computeCharLps(char[] pattern) {
        Objects.requireNonNull(pattern, "pattern must not be null");
        int m = pattern.length;
        int[] lps = new int[m];
        int len = 0;
        int i = 1;
        while (i < m) {
            if (pattern[i] == pattern[len]) {
                len++;
                lps[i] = len;
                i++;
            } else if (len != 0) {
                len = lps[len - 1];
            } else {
                lps[i] = 0;
                i++;
            }
        }
        return lps;
    }

    /**
     * Computes the LPS (Longest Proper Prefix which is also Suffix) failure table for a byte pattern in O(M) time.
     */
    public static int[] computeByteLps(byte[] pattern) {
        Objects.requireNonNull(pattern, "pattern must not be null");
        int m = pattern.length;
        int[] lps = new int[m];
        int len = 0;
        int i = 1;
        while (i < m) {
            if (pattern[i] == pattern[len]) {
                len++;
                lps[i] = len;
                i++;
            } else if (len != 0) {
                len = lps[len - 1];
            } else {
                lps[i] = 0;
                i++;
            }
        }
        return lps;
    }

    /**
     * Returns a defensive copy of the precomputed character LPS failure table.
     */
    public int[] lpsTable() {
        return Arrays.copyOf(charLps, charLps.length);
    }

    /**
     * Returns the character pattern length.
     */
    public int patternLength() {
        return charPattern.length;
    }

    /**
     * Returns the byte pattern length.
     */
    public int bytePatternLength() {
        return bytePattern.length;
    }

    // =========================================================================
    // indexOf methods
    // =========================================================================

    /**
     * Finds the first occurrence index of the pattern in {@code text}, or {@code -1} if not found.
     */
    public int indexOf(CharSequence text) {
        return indexOf(text, 0);
    }

    /**
     * Finds the first occurrence index of the pattern in {@code text} starting at {@code fromIndex}, or {@code -1}.
     */
    public int indexOf(CharSequence text, int fromIndex) {
        Objects.requireNonNull(text, "text must not be null");
        int n = text.length();
        int m = charPattern.length;
        int start = Math.max(0, fromIndex);
        if (n - start < m) {
            return -1;
        }
        int j = 0;
        for (int i = start; i < n; i++) {
            char c = text.charAt(i);
            while (j > 0 && c != charPattern[j]) {
                j = charLps[j - 1];
            }
            if (c == charPattern[j]) {
                j++;
                if (j == m) {
                    return i - m + 1;
                }
            }
        }
        return -1;
    }

    /**
     * Finds the first occurrence index of the pattern in {@code text}, or {@code -1} if not found.
     */
    public int indexOf(char[] text) {
        return indexOf(text, 0);
    }

    /**
     * Finds the first occurrence index of the pattern in {@code text} starting at {@code fromIndex}, or {@code -1}.
     */
    public int indexOf(char[] text, int fromIndex) {
        Objects.requireNonNull(text, "text must not be null");
        int n = text.length;
        int m = charPattern.length;
        int start = Math.max(0, fromIndex);
        if (n - start < m) {
            return -1;
        }
        int j = 0;
        for (int i = start; i < n; i++) {
            char c = text[i];
            while (j > 0 && c != charPattern[j]) {
                j = charLps[j - 1];
            }
            if (c == charPattern[j]) {
                j++;
                if (j == m) {
                    return i - m + 1;
                }
            }
        }
        return -1;
    }

    /**
     * Finds the first occurrence index of the byte pattern in {@code data}, or {@code -1} if not found.
     */
    public int indexOf(byte[] data) {
        return indexOf(data, 0);
    }

    /**
     * Finds the first occurrence index of the byte pattern in {@code data} starting at {@code fromIndex}, or {@code -1}.
     */
    public int indexOf(byte[] data, int fromIndex) {
        Objects.requireNonNull(data, "data must not be null");
        int n = data.length;
        int m = bytePattern.length;
        int start = Math.max(0, fromIndex);
        if (n - start < m) {
            return -1;
        }
        int j = 0;
        for (int i = start; i < n; i++) {
            byte b = data[i];
            while (j > 0 && b != bytePattern[j]) {
                j = byteLps[j - 1];
            }
            if (b == bytePattern[j]) {
                j++;
                if (j == m) {
                    return i - m + 1;
                }
            }
        }
        return -1;
    }

    /**
     * Streams through {@code reader} in O(1) memory and returns the 0-based character offset of the first match,
     * or {@code -1L} if not found.
     */
    public long indexOf(Reader reader) throws IOException {
        Objects.requireNonNull(reader, "reader must not be null");
        char[] buf = new char[STREAM_BUFFER_SIZE];
        int m = charPattern.length;
        int j = 0;
        long totalRead = 0L;
        int read;
        while ((read = reader.read(buf)) != -1) {
            for (int i = 0; i < read; i++) {
                char c = buf[i];
                while (j > 0 && c != charPattern[j]) {
                    j = charLps[j - 1];
                }
                if (c == charPattern[j]) {
                    j++;
                    if (j == m) {
                        return (totalRead + i) - m + 1;
                    }
                }
            }
            totalRead += read;
        }
        return -1L;
    }

    /**
     * Streams through {@code inputStream} in O(1) memory and returns the 0-based byte offset of the first match,
     * or {@code -1L} if not found.
     */
    public long indexOf(InputStream inputStream) throws IOException {
        Objects.requireNonNull(inputStream, "inputStream must not be null");
        byte[] buf = new byte[STREAM_BUFFER_SIZE];
        int m = bytePattern.length;
        int j = 0;
        long totalRead = 0L;
        int read;
        while ((read = inputStream.read(buf)) != -1) {
            for (int i = 0; i < read; i++) {
                byte b = buf[i];
                while (j > 0 && b != bytePattern[j]) {
                    j = byteLps[j - 1];
                }
                if (b == bytePattern[j]) {
                    j++;
                    if (j == m) {
                        return (totalRead + i) - m + 1;
                    }
                }
            }
            totalRead += read;
        }
        return -1L;
    }

    // =========================================================================
    // findAll methods (overlapping & non-overlapping)
    // =========================================================================

    /**
     * Finds all match starting indices in {@code text} (overlapping matches enabled by default).
     */
    public List<Integer> findAll(CharSequence text) {
        return findAll(text, true);
    }

    /**
     * Finds all match starting indices in {@code text}.
     *
     * @param text             Target character sequence
     * @param allowOverlapping If true, jumps via {@code j = lps[j - 1]} on match; if false, resets {@code j = 0}
     */
    public List<Integer> findAll(CharSequence text, boolean allowOverlapping) {
        Objects.requireNonNull(text, "text must not be null");
        int n = text.length();
        int m = charPattern.length;
        if (n < m) {
            return List.of();
        }
        List<Integer> matches = new ArrayList<>();
        int j = 0;
        for (int i = 0; i < n; i++) {
            char c = text.charAt(i);
            while (j > 0 && c != charPattern[j]) {
                j = charLps[j - 1];
            }
            if (c == charPattern[j]) {
                j++;
                if (j == m) {
                    matches.add(i - m + 1);
                    j = allowOverlapping ? charLps[j - 1] : 0;
                }
            }
        }
        return matches;
    }

    /**
     * Finds all match starting indices in {@code text} (overlapping matches enabled by default).
     */
    public List<Integer> findAll(char[] text) {
        return findAll(text, true);
    }

    /**
     * Finds all match starting indices in {@code text}.
     */
    public List<Integer> findAll(char[] text, boolean allowOverlapping) {
        Objects.requireNonNull(text, "text must not be null");
        int n = text.length;
        int m = charPattern.length;
        if (n < m) {
            return List.of();
        }
        List<Integer> matches = new ArrayList<>();
        int j = 0;
        for (int i = 0; i < n; i++) {
            char c = text[i];
            while (j > 0 && c != charPattern[j]) {
                j = charLps[j - 1];
            }
            if (c == charPattern[j]) {
                j++;
                if (j == m) {
                    matches.add(i - m + 1);
                    j = allowOverlapping ? charLps[j - 1] : 0;
                }
            }
        }
        return matches;
    }

    /**
     * Finds all match starting byte indices in {@code data} (overlapping matches enabled by default).
     */
    public List<Integer> findAll(byte[] data) {
        return findAll(data, true);
    }

    /**
     * Finds all match starting byte indices in {@code data}.
     */
    public List<Integer> findAll(byte[] data, boolean allowOverlapping) {
        Objects.requireNonNull(data, "data must not be null");
        int n = data.length;
        int m = bytePattern.length;
        if (n < m) {
            return List.of();
        }
        List<Integer> matches = new ArrayList<>();
        int j = 0;
        for (int i = 0; i < n; i++) {
            byte b = data[i];
            while (j > 0 && b != bytePattern[j]) {
                j = byteLps[j - 1];
            }
            if (b == bytePattern[j]) {
                j++;
                if (j == m) {
                    matches.add(i - m + 1);
                    j = allowOverlapping ? byteLps[j - 1] : 0;
                }
            }
        }
        return matches;
    }

    // =========================================================================
    // countMatches methods
    // =========================================================================

    /**
     * Counts overlapping pattern occurrences in {@code text}.
     */
    public int countMatches(CharSequence text) {
        return countMatches(text, true);
    }

    /**
     * Counts pattern occurrences in {@code text}.
     */
    public int countMatches(CharSequence text, boolean allowOverlapping) {
        Objects.requireNonNull(text, "text must not be null");
        int n = text.length();
        int m = charPattern.length;
        if (n < m) {
            return 0;
        }
        int count = 0;
        int j = 0;
        for (int i = 0; i < n; i++) {
            char c = text.charAt(i);
            while (j > 0 && c != charPattern[j]) {
                j = charLps[j - 1];
            }
            if (c == charPattern[j]) {
                j++;
                if (j == m) {
                    count++;
                    j = allowOverlapping ? charLps[j - 1] : 0;
                }
            }
        }
        return count;
    }

    /**
     * Counts pattern occurrences in a streaming {@link Reader} without buffering the full stream in memory.
     */
    public long countMatches(Reader reader, boolean allowOverlapping) throws IOException {
        Objects.requireNonNull(reader, "reader must not be null");
        char[] buf = new char[STREAM_BUFFER_SIZE];
        int m = charPattern.length;
        int j = 0;
        long count = 0L;
        int read;
        while ((read = reader.read(buf)) != -1) {
            for (int i = 0; i < read; i++) {
                char c = buf[i];
                while (j > 0 && c != charPattern[j]) {
                    j = charLps[j - 1];
                }
                if (c == charPattern[j]) {
                    j++;
                    if (j == m) {
                        count++;
                        j = allowOverlapping ? charLps[j - 1] : 0;
                    }
                }
            }
        }
        return count;
    }

    /**
     * Counts byte pattern occurrences in a streaming {@link InputStream} without buffering the full stream in memory.
     */
    public long countMatches(InputStream inputStream, boolean allowOverlapping) throws IOException {
        Objects.requireNonNull(inputStream, "inputStream must not be null");
        byte[] buf = new byte[STREAM_BUFFER_SIZE];
        int m = bytePattern.length;
        int j = 0;
        long count = 0L;
        int read;
        while ((read = inputStream.read(buf)) != -1) {
            for (int i = 0; i < read; i++) {
                byte b = buf[i];
                while (j > 0 && b != bytePattern[j]) {
                    j = byteLps[j - 1];
                }
                if (b == bytePattern[j]) {
                    j++;
                    if (j == m) {
                        count++;
                        j = allowOverlapping ? byteLps[j - 1] : 0;
                    }
                }
            }
        }
        return count;
    }

    // =========================================================================
    // Lazy Zero-Allocation Streaming Matches: streamMatches(Reader / InputStream)
    // =========================================================================

    /**
     * Lazily streams 0-based character match offsets from {@code reader} using a fixed-size chunk buffer
     * without loading the entire stream into memory (overlapping matches enabled by default).
     */
    public LongStream streamMatches(Reader reader) {
        return streamMatches(reader, true);
    }

    /**
     * Lazily streams 0-based character match offsets from {@code reader} using a fixed-size chunk buffer.
     *
     * @param reader           Source character stream
     * @param allowOverlapping Whether overlapping matches are emitted
     * @return Lazy {@link LongStream} of 0-based match starting positions
     */
    public LongStream streamMatches(Reader reader, boolean allowOverlapping) {
        Objects.requireNonNull(reader, "reader must not be null");
        Spliterator.OfLong spliterator = new Spliterators.AbstractLongSpliterator(
                Long.MAX_VALUE,
                Spliterator.ORDERED | Spliterator.NONNULL | Spliterator.IMMUTABLE
        ) {
            private final char[] buf = new char[STREAM_BUFFER_SIZE];
            private int bufPos = 0;
            private int bufLen = 0;
            private long baseOffset = 0L;
            private int j = 0;
            private boolean eof = false;

            @Override
            public boolean tryAdvance(LongConsumer action) {
                int m = charPattern.length;
                while (!eof) {
                    while (bufPos < bufLen) {
                        char c = buf[bufPos];
                        long currentGlobalIdx = baseOffset + bufPos;
                        bufPos++;
                        while (j > 0 && c != charPattern[j]) {
                            j = charLps[j - 1];
                        }
                        if (c == charPattern[j]) {
                            j++;
                            if (j == m) {
                                j = allowOverlapping ? charLps[j - 1] : 0;
                                action.accept(currentGlobalIdx - m + 1);
                                return true;
                            }
                        }
                    }
                    baseOffset += bufLen;
                    bufPos = 0;
                    try {
                        bufLen = reader.read(buf);
                        if (bufLen == -1) {
                            bufLen = 0;
                            eof = true;
                        }
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }
                return false;
            }
        };
        return StreamSupport.longStream(spliterator, false);
    }

    /**
     * Lazily streams 0-based byte match offsets from {@code inputStream} using a fixed-size chunk buffer
     * without loading the entire stream into memory (overlapping matches enabled by default).
     */
    public LongStream streamMatches(InputStream inputStream) {
        return streamMatches(inputStream, true);
    }

    /**
     * Lazily streams 0-based byte match offsets from {@code inputStream} using a fixed-size chunk buffer.
     */
    public LongStream streamMatches(InputStream inputStream, boolean allowOverlapping) {
        Objects.requireNonNull(inputStream, "inputStream must not be null");
        Spliterator.OfLong spliterator = new Spliterators.AbstractLongSpliterator(
                Long.MAX_VALUE,
                Spliterator.ORDERED | Spliterator.NONNULL | Spliterator.IMMUTABLE
        ) {
            private final byte[] buf = new byte[STREAM_BUFFER_SIZE];
            private int bufPos = 0;
            private int bufLen = 0;
            private long baseOffset = 0L;
            private int j = 0;
            private boolean eof = false;

            @Override
            public boolean tryAdvance(LongConsumer action) {
                int m = bytePattern.length;
                while (!eof) {
                    while (bufPos < bufLen) {
                        byte b = buf[bufPos];
                        long currentGlobalIdx = baseOffset + bufPos;
                        bufPos++;
                        while (j > 0 && b != bytePattern[j]) {
                            j = byteLps[j - 1];
                        }
                        if (b == bytePattern[j]) {
                            j++;
                            if (j == m) {
                                j = allowOverlapping ? byteLps[j - 1] : 0;
                                action.accept(currentGlobalIdx - m + 1);
                                return true;
                            }
                        }
                    }
                    baseOffset += bufLen;
                    bufPos = 0;
                    try {
                        bufLen = inputStream.read(buf);
                        if (bufLen == -1) {
                            bufLen = 0;
                            eof = true;
                        }
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }
                return false;
            }
        };
        return StreamSupport.longStream(spliterator, false);
    }
}
