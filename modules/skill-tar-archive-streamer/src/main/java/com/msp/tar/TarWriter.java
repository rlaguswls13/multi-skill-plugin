package com.msp.tar;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Streaming POSIX UStar TAR archive builder.
 */
public final class TarWriter {

    private final List<TarArchiveEntry> entries = new ArrayList<>();

    public TarWriter() {
    }

    /**
     * Adds a regular file entry to the archive.
     *
     * @param name                     file path in the archive
     * @param data                     raw file byte content
     * @param lastModifiedEpochSeconds modification timestamp in seconds
     * @return this writer instance for chaining
     */
    public TarWriter putEntry(String name, byte[] data, long lastModifiedEpochSeconds) {
        Objects.requireNonNull(name, "name must not be null");
        byte[] payload = (data == null) ? new byte[0] : data;
        TarEntry meta = TarEntry.file(name, payload.length, lastModifiedEpochSeconds);
        entries.add(new TarArchiveEntry(meta, payload));
        return this;
    }

    /**
     * Adds a regular file entry with current epoch seconds.
     */
    public TarWriter putEntry(String name, byte[] data) {
        return putEntry(name, data, Instant.now().getEpochSecond());
    }

    /**
     * Adds a text file entry with UTF-8 encoding.
     */
    public TarWriter putEntry(String name, String content) {
        Objects.requireNonNull(content, "content must not be null");
        return putEntry(name, content.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Adds a directory entry to the archive.
     *
     * @param name directory path (trailing slash will be normalized)
     * @return this writer instance for chaining
     */
    public TarWriter putDirectory(String name) {
        return putDirectory(name, Instant.now().getEpochSecond());
    }

    /**
     * Adds a directory entry with specified modification timestamp.
     */
    public TarWriter putDirectory(String name, long lastModifiedEpochSeconds) {
        Objects.requireNonNull(name, "name must not be null");
        String dirName = name.endsWith("/") ? name : name + "/";
        TarEntry meta = TarEntry.directory(dirName, lastModifiedEpochSeconds);
        entries.add(new TarArchiveEntry(meta, new byte[0]));
        return this;
    }

    /**
     * Writes all queued entries to the provided OutputStream in standard POSIX UStar format
     * and concludes with two 512-byte null blocks.
     *
     * @param out target output stream
     * @throws IOException on I/O error
     */
    public void writeTo(OutputStream out) throws IOException {
        Objects.requireNonNull(out, "out must not be null");
        for (TarArchiveEntry entry : entries) {
            byte[] header = createUstarHeader(entry);
            out.write(header);

            if (!entry.isDirectory()) {
                byte[] data = entry.content();
                if (data.length > 0) {
                    out.write(data);
                    int pad = (512 - (data.length % 512)) % 512;
                    if (pad > 0) {
                        out.write(new byte[pad]);
                    }
                }
            }
        }

        // Conclude with two 512-byte null blocks (1024 bytes)
        out.write(new byte[1024]);
        out.flush();
    }

    /**
     * Compiles all entries into a complete byte array.
     */
    public byte[] toByteArray() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        writeTo(baos);
        return baos.toByteArray();
    }

    private byte[] createUstarHeader(TarArchiveEntry entry) {
        byte[] header = new byte[512];
        boolean isDir = entry.isDirectory();

        // 1. name: [0, 100)
        byte[] nameBytes = entry.name().getBytes(StandardCharsets.UTF_8);
        System.arraycopy(nameBytes, 0, header, 0, Math.min(nameBytes.length, 100));

        // 2. mode: [100, 108) -> "0000755\0" or "0000644\0"
        String mode = isDir ? "0000755\0" : "0000644\0";
        System.arraycopy(mode.getBytes(StandardCharsets.US_ASCII), 0, header, 100, 8);

        // 3. uid: [108, 116)
        System.arraycopy("0000000\0".getBytes(StandardCharsets.US_ASCII), 0, header, 108, 8);

        // 4. gid: [116, 124)
        System.arraycopy("0000000\0".getBytes(StandardCharsets.US_ASCII), 0, header, 116, 8);

        // 5. size: [124, 136) -> 11 octal digits + null
        long size = isDir ? 0L : entry.size();
        String sizeStr = String.format("%011o\0", size);
        System.arraycopy(sizeStr.getBytes(StandardCharsets.US_ASCII), 0, header, 124, 12);

        // 6. mtime: [136, 148) -> 11 octal digits + null
        String mtimeStr = String.format("%011o\0", entry.lastModifiedEpochSeconds());
        System.arraycopy(mtimeStr.getBytes(StandardCharsets.US_ASCII), 0, header, 136, 12);

        // 7. chksum: [148, 156) filled with spaces for checksum calculation
        Arrays.fill(header, 148, 156, (byte) ' ');

        // 8. typeflag: offset 156 ('5' for directory, '0' for regular file)
        header[156] = (byte) (isDir ? '5' : '0');

        // 9. magic: [257, 263) -> "ustar\0"
        byte[] magic = new byte[]{'u', 's', 't', 'a', 'r', 0};
        System.arraycopy(magic, 0, header, 257, 6);

        // 10. version: [263, 265) -> "00"
        byte[] version = new byte[]{'0', '0'};
        System.arraycopy(version, 0, header, 263, 2);

        // 11. uname: [265, 297) -> "root\0"
        byte[] uname = "root\0".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(uname, 0, header, 265, uname.length);

        // 12. gname: [297, 329) -> "root\0"
        byte[] gname = "root\0".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(gname, 0, header, 297, gname.length);

        // 13. Calculate checksum
        long sum = 0;
        for (byte b : header) {
            sum += (b & 0xFF);
        }
        // Format: 6 octal digits + null + space
        String chksumStr = String.format("%06o\0 ", sum);
        System.arraycopy(chksumStr.getBytes(StandardCharsets.US_ASCII), 0, header, 148, 8);

        return header;
    }
}
