package com.msp.targz;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Pure Java streaming extractor and packager for .tar.gz and .tgz archives.
 * <p>
 * Features:
 * <ul>
 *     <li>Zip Slip (path traversal) vulnerability prevention</li>
 *     <li>Streaming I/O without loading entire archives into memory</li>
 *     <li>Support for POSIX USTAR standard & GNU LongLink ('L') headers</li>
 *     <li>Directory structure and file timestamp preservation</li>
 * </ul>
 */
public final class TarGzExtractor {

    private static final int RECORD_SIZE = 512;
    private static final byte LF_NORMAL = '0';
    private static final byte LF_DIR = '5';
    private static final byte LF_GNUTYPE_LONGNAME = 'L';

    private TarGzExtractor() {
        // utility class
    }

    /**
     * Extracts all entries from a .tar.gz / .tgz input stream into the specified target directory.
     * Prevents Zip Slip path traversal attacks by validating entry paths.
     *
     * @param tarGzStream compressed tar stream
     * @param targetDir   destination directory
     * @return list of extracted files and directories
     * @throws IOException       if an I/O error occurs
     * @throws SecurityException if an entry attempts path traversal outside targetDir
     */
    public static List<File> extractToDirectory(InputStream tarGzStream, File targetDir) throws IOException {
        Objects.requireNonNull(tarGzStream, "tarGzStream must not be null");
        Objects.requireNonNull(targetDir, "targetDir must not be null");

        Path targetRoot = targetDir.toPath().toAbsolutePath().normalize();
        if (!Files.exists(targetRoot)) {
            Files.createDirectories(targetRoot);
        }

        List<File> extractedFiles = new ArrayList<>();
        byte[] buffer = new byte[8192];
        byte[] headerBlock = new byte[RECORD_SIZE];

        try (GZIPInputStream gzipIn = new GZIPInputStream(new BufferedInputStream(tarGzStream))) {
            String pendingLongName = null;

            while (readExact(gzipIn, headerBlock, RECORD_SIZE)) {
                if (isAllZeros(headerBlock)) {
                    // Archive end reached (EOF block)
                    break;
                }

                TarHeader header = parseHeader(headerBlock);
                String entryName = pendingLongName != null ? pendingLongName : header.name;
                pendingLongName = null;

                if (header.typeFlag == LF_GNUTYPE_LONGNAME) {
                    // Read long name content
                    byte[] longNameBytes = readBytes(gzipIn, header.size);
                    pendingLongName = extractNullTerminatedString(longNameBytes);
                    skipPadding(gzipIn, header.size);
                    continue;
                }

                // Zip Slip defense
                Path resolvedPath = targetRoot.resolve(entryName).normalize().toAbsolutePath();
                if (!resolvedPath.startsWith(targetRoot)) {
                    throw new SecurityException("Zip Slip path traversal blocked: " + entryName);
                }

                if (header.isDirectory || entryName.endsWith("/")) {
                    Files.createDirectories(resolvedPath);
                    extractedFiles.add(resolvedPath.toFile());
                    skipPadding(gzipIn, header.size);
                } else {
                    Path parent = resolvedPath.getParent();
                    if (parent != null && !Files.exists(parent)) {
                        Files.createDirectories(parent);
                    }

                    try (OutputStream out = new BufferedOutputStream(new FileOutputStream(resolvedPath.toFile()))) {
                        long remaining = header.size;
                        while (remaining > 0) {
                            int toRead = (int) Math.min(buffer.length, remaining);
                            int read = gzipIn.read(buffer, 0, toRead);
                            if (read == -1) {
                                throw new EOFException("Unexpected EOF while reading entry: " + entryName);
                            }
                            out.write(buffer, 0, read);
                            remaining -= read;
                        }
                    }

                    if (header.mtime != null) {
                        try {
                            Files.setLastModifiedTime(resolvedPath, FileTime.from(header.mtime));
                        } catch (Exception ignored) {
                            // Best-effort modification time setting
                        }
                    }

                    extractedFiles.add(resolvedPath.toFile());
                    skipPadding(gzipIn, header.size);
                }
            }
        }

        return extractedFiles;
    }

    /**
     * Lists all entries in a .tar.gz / .tgz archive without extracting them to disk.
     *
     * @param tarGzStream compressed tar stream
     * @return list of TarEntryInfo records
     * @throws IOException if an I/O error occurs
     */
    public static List<TarEntryInfo> listEntries(InputStream tarGzStream) throws IOException {
        Objects.requireNonNull(tarGzStream, "tarGzStream must not be null");

        List<TarEntryInfo> entries = new ArrayList<>();
        byte[] headerBlock = new byte[RECORD_SIZE];

        try (GZIPInputStream gzipIn = new GZIPInputStream(new BufferedInputStream(tarGzStream))) {
            String pendingLongName = null;

            while (readExact(gzipIn, headerBlock, RECORD_SIZE)) {
                if (isAllZeros(headerBlock)) {
                    break;
                }

                TarHeader header = parseHeader(headerBlock);
                String entryName = pendingLongName != null ? pendingLongName : header.name;
                pendingLongName = null;

                if (header.typeFlag == LF_GNUTYPE_LONGNAME) {
                    byte[] longNameBytes = readBytes(gzipIn, header.size);
                    pendingLongName = extractNullTerminatedString(longNameBytes);
                    skipPadding(gzipIn, header.size);
                    continue;
                }

                entries.add(new TarEntryInfo(
                        entryName,
                        header.size,
                        header.isDirectory || entryName.endsWith("/"),
                        header.mtime != null ? header.mtime : Instant.EPOCH
                ));

                // Skip payload and padding
                long totalToSkip = header.size + getPaddingSize(header.size);
                skipFully(gzipIn, totalToSkip);
            }
        }

        return entries;
    }

    /**
     * Compresses the contents of sourceDir into a .tar.gz archive written to tarGzOut.
     *
     * @param sourceDir directory whose contents to archive
     * @param tarGzOut  output stream where compressed data is written
     * @throws IOException if an I/O error occurs
     */
    public static void createArchive(File sourceDir, OutputStream tarGzOut) throws IOException {
        Objects.requireNonNull(sourceDir, "sourceDir must not be null");
        Objects.requireNonNull(tarGzOut, "tarGzOut must not be null");

        if (!sourceDir.isDirectory()) {
            throw new IllegalArgumentException("sourceDir must be a directory: " + sourceDir);
        }

        Path rootPath = sourceDir.toPath().toAbsolutePath().normalize();

        try (GZIPOutputStream gzipOut = new GZIPOutputStream(new BufferedOutputStream(tarGzOut))) {
            Files.walkFileTree(rootPath, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    if (dir.equals(rootPath)) {
                        return FileVisitResult.CONTINUE;
                    }

                    String relName = rootPath.relativize(dir).toString().replace('\\', '/') + "/";
                    writeEntryHeader(gzipOut, relName, 0, true, attrs.lastModifiedTime().toInstant());
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    String relName = rootPath.relativize(file).toString().replace('\\', '/');
                    long size = attrs.size();
                    Instant mtime = attrs.lastModifiedTime().toInstant();

                    writeEntryHeader(gzipOut, relName, size, false, mtime);

                    try (InputStream fis = new BufferedInputStream(new FileInputStream(file.toFile()))) {
                        byte[] buf = new byte[8192];
                        long written = 0;
                        int r;
                        while ((r = fis.read(buf)) != -1) {
                            gzipOut.write(buf, 0, r);
                            written += r;
                        }
                    }

                    int pad = getPaddingSize(size);
                    if (pad > 0) {
                        gzipOut.write(new byte[pad]);
                    }

                    return FileVisitResult.CONTINUE;
                }
            });

            // Write 2 blocks of 512 zero bytes to mark end of archive
            gzipOut.write(new byte[RECORD_SIZE * 2]);
            gzipOut.finish();
        }
    }

    private static void writeEntryHeader(OutputStream out, String name, long size, boolean isDir, Instant mtime) throws IOException {
        byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);

        // If filename > 100 bytes, emit GNU LongLink header
        if (nameBytes.length > 100) {
            byte[] longHeader = new byte[RECORD_SIZE];
            // Header for long name itself
            writeString(longHeader, 0, 100, "././@LongLink");
            writeOctal(longHeader, 100, 8, 0644);
            writeOctal(longHeader, 108, 8, 0);
            writeOctal(longHeader, 116, 8, 0);
            writeOctal(longHeader, 124, 12, nameBytes.length + 1); // include null terminator
            writeOctal(longHeader, 136, 12, mtime.getEpochSecond());
            longHeader[156] = LF_GNUTYPE_LONGNAME;
            writeUstarMagic(longHeader);
            computeAndWriteChecksum(longHeader);

            out.write(longHeader);

            // Long name payload + padding
            out.write(nameBytes);
            out.write(0); // null terminator
            int pad = getPaddingSize(nameBytes.length + 1);
            if (pad > 0) {
                out.write(new byte[pad]);
            }
        }

        byte[] header = new byte[RECORD_SIZE];
        String truncatedName = nameBytes.length > 100 ? name.substring(0, 100) : name;
        writeString(header, 0, 100, truncatedName);
        writeOctal(header, 100, 8, isDir ? 0755 : 0644);
        writeOctal(header, 108, 8, 0); // UID
        writeOctal(header, 116, 8, 0); // GID
        writeOctal(header, 124, 12, isDir ? 0 : size);
        writeOctal(header, 136, 12, mtime.getEpochSecond());
        header[156] = isDir ? LF_DIR : LF_NORMAL;
        writeUstarMagic(header);
        computeAndWriteChecksum(header);

        out.write(header);
    }

    private static void writeUstarMagic(byte[] block) {
        // "ustar\0" and "00"
        byte[] magic = "ustar\00000".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(magic, 0, block, 257, magic.length);
    }

    private static void writeString(byte[] block, int offset, int length, String str) {
        byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
        int copyLen = Math.min(bytes.length, length);
        System.arraycopy(bytes, 0, block, offset, copyLen);
    }

    private static void writeOctal(byte[] block, int offset, int length, long val) {
        String s = Long.toOctalString(val);
        int formattedLen = length - 1; // leave room for space/null
        StringBuilder sb = new StringBuilder();
        while (sb.length() + s.length() < formattedLen) {
            sb.append('0');
        }
        sb.append(s);
        sb.append(' ');
        byte[] bytes = sb.toString().getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(bytes, 0, block, offset, Math.min(bytes.length, length));
    }

    private static void computeAndWriteChecksum(byte[] header) {
        // Fill checksum field with spaces (0x20)
        Arrays.fill(header, 148, 148 + 8, (byte) ' ');
        long sum = 0;
        for (byte b : header) {
            sum += (b & 0xFF);
        }
        String s = Long.toOctalString(sum);
        StringBuilder sb = new StringBuilder();
        while (sb.length() + s.length() < 6) {
            sb.append('0');
        }
        sb.append(s);
        sb.append('\0');
        sb.append(' ');
        byte[] bytes = sb.toString().getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(bytes, 0, header, 148, 8);
    }

    private static TarHeader parseHeader(byte[] block) {
        String name = extractNullTerminatedString(block, 0, 100);
        long size = parseOctal(block, 124, 12);
        long mtimeSec = parseOctal(block, 136, 12);
        byte typeFlag = block[156];
        if (typeFlag == 0) {
            typeFlag = LF_NORMAL;
        }

        // Check for USTAR prefix (bytes 345..499)
        String prefix = extractNullTerminatedString(block, 345, 155);
        if (!prefix.isEmpty()) {
            name = prefix + "/" + name;
        }

        boolean isDir = (typeFlag == LF_DIR);
        Instant mtime = Instant.ofEpochSecond(mtimeSec);

        return new TarHeader(name, size, isDir, mtime, typeFlag);
    }

    private static long parseOctal(byte[] block, int offset, int length) {
        String s = new String(block, offset, length, StandardCharsets.US_ASCII).trim();
        if (s.isEmpty()) {
            return 0;
        }
        int nullIdx = s.indexOf('\0');
        if (nullIdx >= 0) {
            s = s.substring(0, nullIdx).trim();
        }
        if (s.isEmpty()) {
            return 0;
        }
        try {
            return Long.parseLong(s, 8);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String extractNullTerminatedString(byte[] block, int offset, int length) {
        int end = offset;
        while (end < offset + length && block[end] != 0) {
            end++;
        }
        return new String(block, offset, end - offset, StandardCharsets.UTF_8).trim();
    }

    private static String extractNullTerminatedString(byte[] bytes) {
        return extractNullTerminatedString(bytes, 0, bytes.length);
    }

    private static boolean isAllZeros(byte[] block) {
        for (byte b : block) {
            if (b != 0) return false;
        }
        return true;
    }

    private static int getPaddingSize(long size) {
        return (int) ((RECORD_SIZE - (size % RECORD_SIZE)) % RECORD_SIZE);
    }

    private static void skipPadding(InputStream in, long size) throws IOException {
        int pad = getPaddingSize(size);
        if (pad > 0) {
            skipFully(in, pad);
        }
    }

    private static boolean readExact(InputStream in, byte[] buf, int len) throws IOException {
        int total = 0;
        while (total < len) {
            int r = in.read(buf, total, len - total);
            if (r == -1) {
                if (total == 0) return false;
                throw new EOFException("Unexpected EOF while reading tar record");
            }
            total += r;
        }
        return true;
    }

    private static byte[] readBytes(InputStream in, long size) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream((int) Math.min(size, 65536));
        byte[] buf = new byte[4096];
        long remaining = size;
        while (remaining > 0) {
            int toRead = (int) Math.min(buf.length, remaining);
            int r = in.read(buf, 0, toRead);
            if (r == -1) {
                throw new EOFException("Unexpected EOF while reading payload");
            }
            baos.write(buf, 0, r);
            remaining -= r;
        }
        return baos.toByteArray();
    }

    private static void skipFully(InputStream in, long bytesToSkip) throws IOException {
        long remaining = bytesToSkip;
        byte[] drain = new byte[4096];
        while (remaining > 0) {
            int toRead = (int) Math.min(drain.length, remaining);
            int r = in.read(drain, 0, toRead);
            if (r == -1) {
                throw new EOFException("Unexpected EOF while skipping " + remaining + " bytes");
            }
            remaining -= r;
        }
    }

    private record TarHeader(
            String name,
            long size,
            boolean isDirectory,
            Instant mtime,
            byte typeFlag
    ) {
    }
}
