package com.msp.gzipstream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;
import java.util.zip.ZipException;

/**
 * Thread-safe utility pipeline for streaming and in-memory compression and decompression
 * supporting standard GZIP and DEFLATE algorithms.
 */
public final class GzipStreamer {

    public static final int GZIP_MAGIC_FIRST = 0x1F;
    public static final int GZIP_MAGIC_SECOND = 0x8B;
    private static final int BUFFER_SIZE = 8192;

    private GzipStreamer() {
        // Utility class
    }

    /**
     * Checks if the given byte array begins with the GZIP magic header (0x1F, 0x8B).
     *
     * @param header byte array containing at least 2 bytes of header data
     * @return true if the magic header matches GZIP format, false otherwise
     */
    public static boolean isGzip(byte[] header) {
        if (header == null || header.length < 2) {
            return false;
        }
        return (header[0] & 0xFF) == GZIP_MAGIC_FIRST && (header[1] & 0xFF) == GZIP_MAGIC_SECOND;
    }

    /**
     * Inspects an {@link InputStream} with mark support to detect GZIP magic header without consuming it.
     *
     * @param in input stream that supports mark/reset
     * @return true if GZIP header detected, false otherwise
     * @throws IOException if I/O error occurs
     */
    public static boolean isGzip(InputStream in) throws IOException {
        if (in == null || !in.markSupported()) {
            return false;
        }
        in.mark(2);
        try {
            int b1 = in.read();
            int b2 = in.read();
            return b1 == GZIP_MAGIC_FIRST && b2 == GZIP_MAGIC_SECOND;
        } finally {
            in.reset();
        }
    }

    /**
     * Compresses raw bytes using standard GZIP format.
     *
     * @param data raw uncompressed bytes (not null)
     * @return GZIP compressed byte array
     * @throws CompressionException if compression fails
     */
    public static byte[] compressGzip(byte[] data) {
        Objects.requireNonNull(data, "Data byte array cannot be null");
        ByteArrayOutputStream bos = new ByteArrayOutputStream(Math.max(32, data.length / 2));
        try (GZIPOutputStream gzos = new GZIPOutputStream(bos)) {
            gzos.write(data);
        } catch (IOException e) {
            throw new CompressionException("Failed to compress GZIP data", e);
        }
        return bos.toByteArray();
    }

    /**
     * Decompresses GZIP-compressed bytes back to original raw bytes.
     *
     * @param compressed GZIP compressed byte array (not null)
     * @return decompressed raw byte array
     * @throws CompressionException if decompression fails or header is invalid
     */
    public static byte[] decompressGzip(byte[] compressed) {
        Objects.requireNonNull(compressed, "Compressed byte array cannot be null");
        if (!isGzip(compressed)) {
            throw new CompressionException("Invalid GZIP header: missing magic number (0x1F, 0x8B)");
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream(compressed.length * 2);
        try (ByteArrayInputStream bis = new ByteArrayInputStream(compressed);
             GZIPInputStream gzis = new GZIPInputStream(bis, BUFFER_SIZE)) {
            gzis.transferTo(bos);
        } catch (IOException e) {
            throw new CompressionException("Failed to decompress GZIP data", e);
        }
        return bos.toByteArray();
    }

    /**
     * Compresses raw bytes using DEFLATE algorithm with default compression level.
     *
     * @param data raw uncompressed bytes (not null)
     * @return DEFLATE compressed byte array
     */
    public static byte[] compressDeflate(byte[] data) {
        return compressDeflate(data, Deflater.DEFAULT_COMPRESSION);
    }

    /**
     * Compresses raw bytes using DEFLATE algorithm with specified compression level (0-9 or -1).
     *
     * @param data raw uncompressed bytes (not null)
     * @param level compression level (0 to 9, or {@link Deflater#DEFAULT_COMPRESSION})
     * @return DEFLATE compressed byte array
     * @throws CompressionException if compression fails
     */
    public static byte[] compressDeflate(byte[] data, int level) {
        Objects.requireNonNull(data, "Data byte array cannot be null");
        if (level < -1 || level > 9) {
            throw new IllegalArgumentException("Compression level must be between -1 and 9: " + level);
        }
        Deflater deflater = new Deflater(level);
        ByteArrayOutputStream bos = new ByteArrayOutputStream(Math.max(32, data.length / 2));
        try (DeflaterOutputStream dos = new DeflaterOutputStream(bos, deflater)) {
            dos.write(data);
            dos.finish();
        } catch (IOException e) {
            throw new CompressionException("Failed to compress DEFLATE data", e);
        } finally {
            deflater.end();
        }
        return bos.toByteArray();
    }

    /**
     * Decompresses DEFLATE-compressed bytes back to original raw bytes.
     *
     * @param compressed DEFLATE compressed byte array (not null)
     * @return decompressed raw byte array
     * @throws CompressionException if decompression fails
     */
    public static byte[] decompressDeflate(byte[] compressed) {
        Objects.requireNonNull(compressed, "Compressed byte array cannot be null");
        Inflater inflater = new Inflater();
        ByteArrayOutputStream bos = new ByteArrayOutputStream(compressed.length * 2);
        try (ByteArrayInputStream bis = new ByteArrayInputStream(compressed);
             InflaterInputStream iis = new InflaterInputStream(bis, inflater, BUFFER_SIZE)) {
            iis.transferTo(bos);
        } catch (IOException e) {
            throw new CompressionException("Failed to decompress DEFLATE data", e);
        } finally {
            inflater.end();
        }
        return bos.toByteArray();
    }

    /**
     * Compresses input stream into output stream with default compression level.
     * Does NOT close the underlying output stream, but finishes and flushes the compressor.
     *
     * @param in uncompressed source stream (not null)
     * @param out compressed destination stream (not null)
     * @param type compression format (GZIP or DEFLATE)
     * @return total uncompressed bytes read from input stream
     * @throws CompressionException if streaming fails
     */
    public static long compressStream(InputStream in, OutputStream out, CompressionType type) {
        return compressStream(in, out, type, Deflater.DEFAULT_COMPRESSION);
    }

    /**
     * Compresses input stream into output stream with specified compression level.
     * Does NOT close the underlying output stream, but finishes and flushes the compressor.
     *
     * @param in uncompressed source stream (not null)
     * @param out compressed destination stream (not null)
     * @param type compression format (GZIP or DEFLATE)
     * @param level compression level (0-9 or Deflater.DEFAULT_COMPRESSION)
     * @return total uncompressed bytes read from input stream
     * @throws CompressionException if streaming fails
     */
    public static long compressStream(InputStream in, OutputStream out, CompressionType type, int level) {
        Objects.requireNonNull(in, "Input stream cannot be null");
        Objects.requireNonNull(out, "Output stream cannot be null");
        Objects.requireNonNull(type, "CompressionType cannot be null");

        try {
            if (type == CompressionType.GZIP) {
                GZIPOutputStream gzos = new GZIPOutputStream(out, BUFFER_SIZE);
                long bytesRead = in.transferTo(gzos);
                gzos.finish();
                gzos.flush();
                return bytesRead;
            } else {
                Deflater deflater = new Deflater(level);
                try {
                    DeflaterOutputStream dos = new DeflaterOutputStream(out, deflater, BUFFER_SIZE);
                    long bytesRead = in.transferTo(dos);
                    dos.finish();
                    dos.flush();
                    return bytesRead;
                } finally {
                    deflater.end();
                }
            }
        } catch (IOException e) {
            throw new CompressionException("Stream compression failed for " + type, e);
        }
    }

    /**
     * Decompresses compressed input stream into destination output stream.
     * Does NOT close the underlying output stream, but flushes uncompressed bytes.
     *
     * @param in compressed source stream (not null)
     * @param out uncompressed destination stream (not null)
     * @param type compression format (GZIP or DEFLATE)
     * @return total uncompressed bytes written to destination stream
     * @throws CompressionException if decompression fails
     */
    public static long decompressStream(InputStream in, OutputStream out, CompressionType type) {
        Objects.requireNonNull(in, "Input stream cannot be null");
        Objects.requireNonNull(out, "Output stream cannot be null");
        Objects.requireNonNull(type, "CompressionType cannot be null");

        try {
            if (type == CompressionType.GZIP) {
                GZIPInputStream gzis = new GZIPInputStream(in, BUFFER_SIZE);
                long decompressedBytes = gzis.transferTo(out);
                out.flush();
                return decompressedBytes;
            } else {
                Inflater inflater = new Inflater();
                try {
                    InflaterInputStream iis = new InflaterInputStream(in, inflater, BUFFER_SIZE);
                    long decompressedBytes = iis.transferTo(out);
                    out.flush();
                    return decompressedBytes;
                } finally {
                    inflater.end();
                }
            }
        } catch (IOException e) {
            throw new CompressionException("Stream decompression failed for " + type, e);
        }
    }
}
