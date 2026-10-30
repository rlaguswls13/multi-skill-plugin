package com.msp.tar;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Pure Java 21 POSIX UStar TAR archive stream reader.
 */
public final class TarReader {

    private static final int BLOCK_SIZE = 512;

    private TarReader() {
    }

    /**
     * Reads all entries from a TAR input stream.
     *
     * @param in input stream containing TAR archive data
     * @return list of extracted TarArchiveEntry instances
     * @throws IOException on read failure or corrupt TAR format
     */
    public static List<TarArchiveEntry> readEntries(InputStream in) throws IOException {
        Objects.requireNonNull(in, "in must not be null");
        List<TarArchiveEntry> entries = new ArrayList<>();

        while (true) {
            byte[] header = readExactBytes(in, BLOCK_SIZE, false);
            if (header == null || isAllZero(header)) {
                // End of archive reached (either EOF or 512-byte zero block)
                break;
            }

            // 1. Extract name (offset 0, 100 bytes)
            String name = extractString(header, 0, 100);

            // 2. Extract size (offset 124, 12 bytes octal)
            long size = parseOctal(header, 124, 12);

            // 3. Extract mtime (offset 136, 12 bytes octal)
            long mtime = parseOctal(header, 136, 12);

            // 4. Extract typeflag (offset 156)
            byte typeflag = header[156];
            boolean isDirectory = (typeflag == '5') || name.endsWith("/");

            // 5. Read payload
            byte[] content;
            if (isDirectory || size == 0) {
                content = new byte[0];
            } else {
                content = readExactBytes(in, (int) size, true);
                int pad = (BLOCK_SIZE - (int) (size % BLOCK_SIZE)) % BLOCK_SIZE;
                if (pad > 0) {
                    readExactBytes(in, pad, true); // discard padding
                }
            }

            TarEntry meta = new TarEntry(name, isDirectory ? 0L : size, mtime, isDirectory);
            entries.add(new TarArchiveEntry(meta, content));
        }

        return entries;
    }

    /**
     * Reads all entries from a TAR byte array.
     */
    public static List<TarArchiveEntry> readEntries(byte[] tarData) throws IOException {
        Objects.requireNonNull(tarData, "tarData must not be null");
        try (ByteArrayInputStream bais = new ByteArrayInputStream(tarData)) {
            return readEntries(bais);
        }
    }

    private static String extractString(byte[] header, int offset, int length) {
        int end = offset;
        while (end < offset + length && header[end] != 0) {
            end++;
        }
        return new String(header, offset, end - offset, StandardCharsets.UTF_8).trim();
    }

    private static long parseOctal(byte[] header, int offset, int length) {
        String str = extractString(header, offset, length).trim();
        if (str.isEmpty()) {
            return 0L;
        }
        try {
            return Long.parseLong(str, 8);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static boolean isAllZero(byte[] block) {
        for (byte b : block) {
            if (b != 0) {
                return false;
            }
        }
        return true;
    }

    private static byte[] readExactBytes(InputStream in, int length, boolean strict) throws IOException {
        byte[] buffer = new byte[length];
        int totalRead = 0;
        while (totalRead < length) {
            int read = in.read(buffer, totalRead, length - totalRead);
            if (read < 0) {
                if (totalRead == 0 && !strict) {
                    return null;
                }
                throw new IOException("Premature end of TAR stream: expected " + length + " bytes, read " + totalRead);
            }
            totalRead += read;
        }
        return buffer;
    }
}
