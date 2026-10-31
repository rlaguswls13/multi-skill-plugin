package com.msp.httprange;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Objects;

/**
 * Utility for slicing and streaming byte chunks corresponding to a {@link ByteRange}.
 */
public final class RangeChunkExtractor {

    private static final int DEFAULT_BUFFER_SIZE = 8192;

    private RangeChunkExtractor() {
        // Utility class
    }

    /**
     * Extracts byte sub-array for the specified satisfiable {@link ByteRange}.
     *
     * @param data  source byte array
     * @param range target byte range
     * @return sliced byte array
     * @throws IllegalArgumentException if range is unsatisfiable or exceeds array bounds
     */
    public static byte[] extract(byte[] data, ByteRange range) {
        Objects.requireNonNull(data, "data must not be null");
        Objects.requireNonNull(range, "range must not be null");

        if (!range.isSatisfiable()) {
            throw new IllegalArgumentException("Cannot extract unsatisfiable range: " + range);
        }

        if (range.start() < 0 || range.end() >= data.length || range.start() > range.end()) {
            throw new IllegalArgumentException(
                "Range out of array bounds: start=" + range.start() + ", end=" + range.end() + ", data.length=" + data.length
            );
        }

        return Arrays.copyOfRange(data, (int) range.start(), (int) range.end() + 1);
    }

    /**
     * Reads the byte slice defined by {@link ByteRange} from the input stream.
     *
     * @param in    source InputStream
     * @param range target byte range
     * @return extracted byte array
     * @throws IOException              on I/O failure
     * @throws IllegalArgumentException if range is unsatisfiable or length exceeds Integer.MAX_VALUE
     */
    public static byte[] extract(InputStream in, ByteRange range) throws IOException {
        Objects.requireNonNull(in, "in must not be null");
        Objects.requireNonNull(range, "range must not be null");

        if (!range.isSatisfiable()) {
            throw new IllegalArgumentException("Cannot extract unsatisfiable range: " + range);
        }

        long length = range.length();
        if (length > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Range length exceeds maximum byte array size: " + length);
        }

        skipFully(in, range.start());
        return in.readNBytes((int) length);
    }

    /**
     * Returns an {@link InputStream} positioned at {@code range.start()} and bounded to {@code range.length()} bytes.
     *
     * @param in    source InputStream
     * @param range target byte range
     * @return bounded input stream
     * @throws IOException              on I/O failure when skipping
     * @throws IllegalArgumentException if range is unsatisfiable
     */
    public static InputStream extractStream(InputStream in, ByteRange range) throws IOException {
        Objects.requireNonNull(in, "in must not be null");
        Objects.requireNonNull(range, "range must not be null");

        if (!range.isSatisfiable()) {
            throw new IllegalArgumentException("Cannot stream unsatisfiable range: " + range);
        }

        skipFully(in, range.start());
        return new BoundedInputStream(in, range.length());
    }

    /**
     * Copies the content of the specified range from input stream directly to output stream.
     *
     * @param in    source InputStream
     * @param out   destination OutputStream
     * @param range target byte range
     * @return total bytes transferred
     * @throws IOException on I/O failure
     */
    public static long copy(InputStream in, OutputStream out, ByteRange range) throws IOException {
        Objects.requireNonNull(out, "out must not be null");
        try (InputStream stream = extractStream(in, range)) {
            byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
            long totalRead = 0;
            int read;
            while ((read = stream.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                totalRead += read;
            }
            return totalRead;
        }
    }

    private static void skipFully(InputStream in, long bytesToSkip) throws IOException {
        long remaining = bytesToSkip;
        while (remaining > 0) {
            long skipped = in.skip(remaining);
            if (skipped <= 0) {
                // in.skip() may return 0, fallback to reading a single byte
                if (in.read() == -1) {
                    throw new IOException("Unexpected EOF while skipping " + bytesToSkip + " bytes; skipped=" + (bytesToSkip - remaining));
                }
                skipped = 1;
            }
            remaining -= skipped;
        }
    }

    /**
     * Bounded InputStream wrapper that does not allow reading past a given limit.
     */
    private static final class BoundedInputStream extends InputStream {
        private final InputStream delegate;
        private long remaining;

        BoundedInputStream(InputStream delegate, long limit) {
            this.delegate = delegate;
            this.remaining = limit;
        }

        @Override
        public int read() throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            int b = delegate.read();
            if (b != -1) {
                remaining--;
            }
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            int maxToRead = (int) Math.min(len, remaining);
            int read = delegate.read(b, off, maxToRead);
            if (read != -1) {
                remaining -= read;
            }
            return read;
        }

        @Override
        public int available() throws IOException {
            return (int) Math.min(delegate.available(), remaining);
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }
    }
}
