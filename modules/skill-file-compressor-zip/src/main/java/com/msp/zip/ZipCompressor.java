package com.msp.zip;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Thread-safe ZIP compression and decompression utility with built-in path-traversal (ZipSlip)
 * and expansion ratio (ZipBomb) protection.
 */
public final class ZipCompressor {

    private final ZipGuardOptions defaultOptions;

    public ZipCompressor() {
        this(ZipGuardOptions.DEFAULT);
    }

    public ZipCompressor(ZipGuardOptions defaultOptions) {
        this.defaultOptions = Objects.requireNonNull(defaultOptions, "defaultOptions must not be null");
    }

    /**
     * Compresses a single file into a ZIP archive.
     *
     * @param sourceFile path of the file to compress
     * @param zipFile    destination ZIP archive path
     * @throws IOException if an I/O error occurs
     */
    public static void compressFile(Path sourceFile, Path zipFile) throws IOException {
        Objects.requireNonNull(sourceFile, "sourceFile must not be null");
        Objects.requireNonNull(zipFile, "zipFile must not be null");

        if (!Files.exists(sourceFile)) {
            throw new NoSuchFileException("Source file does not exist: " + sourceFile);
        }
        if (Files.isDirectory(sourceFile)) {
            throw new IllegalArgumentException("Source path is a directory, use compressDirectory instead: " + sourceFile);
        }

        Path parent = zipFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        try (OutputStream fos = Files.newOutputStream(zipFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
             BufferedOutputStream bos = new BufferedOutputStream(fos);
             ZipOutputStream zos = new ZipOutputStream(bos)) {

            ZipEntry entry = new ZipEntry(sourceFile.getFileName().toString());
            entry.setTime(Files.getLastModifiedTime(sourceFile).toMillis());
            zos.putNextEntry(entry);
            Files.copy(sourceFile, zos);
            zos.closeEntry();
        }
    }

    /**
     * Recursively compresses a directory into a ZIP archive.
     *
     * @param dir         source directory to compress
     * @param zipFile     destination ZIP archive path
     * @param includeRoot if true, root directory name is included as root entry inside the archive;
     *                    if false, only directory contents are packed at archive root.
     * @throws IOException if an I/O error occurs
     */
    public static void compressDirectory(Path dir, Path zipFile, boolean includeRoot) throws IOException {
        Objects.requireNonNull(dir, "dir must not be null");
        Objects.requireNonNull(zipFile, "zipFile must not be null");

        if (!Files.exists(dir)) {
            throw new NoSuchFileException("Source directory does not exist: " + dir);
        }
        if (!Files.isDirectory(dir)) {
            throw new IllegalArgumentException("Source path is not a directory: " + dir);
        }

        Path parent = zipFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Path baseDir = includeRoot ? dir.getParent() : dir;
        if (baseDir == null) {
            baseDir = dir;
        }

        final Path finalBaseDir = baseDir;
        final Path normalizedZip = zipFile.toAbsolutePath().normalize();

        try (OutputStream fos = Files.newOutputStream(zipFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
             BufferedOutputStream bos = new BufferedOutputStream(fos);
             ZipOutputStream zos = new ZipOutputStream(bos)) {

            Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path subDir, BasicFileAttributes attrs) throws IOException {
                    if (!subDir.equals(dir) || includeRoot) {
                        String relative = finalBaseDir.relativize(subDir).toString().replace('\\', '/');
                        if (!relative.endsWith("/")) {
                            relative += "/";
                        }
                        ZipEntry entry = new ZipEntry(relative);
                        entry.setTime(attrs.lastModifiedTime().toMillis());
                        zos.putNextEntry(entry);
                        zos.closeEntry();
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    // Prevent self-compression loop if destination zip is placed within source dir
                    if (file.toAbsolutePath().normalize().equals(normalizedZip)) {
                        return FileVisitResult.CONTINUE;
                    }
                    String relative = finalBaseDir.relativize(file).toString().replace('\\', '/');
                    ZipEntry entry = new ZipEntry(relative);
                    entry.setTime(attrs.lastModifiedTime().toMillis());
                    zos.putNextEntry(entry);
                    Files.copy(file, zos);
                    zos.closeEntry();
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }

    /**
     * Decompresses an archive into targetDir with default guard options.
     */
    public static void decompress(Path zipFile, Path targetDir, boolean overwrite) throws IOException {
        decompress(zipFile, targetDir, overwrite, ZipGuardOptions.DEFAULT);
    }

    /**
     * Decompresses an archive into targetDir with custom guard options.
     * Guards against ZipSlip (directory traversal) and ZipBomb (extreme expansion).
     *
     * @param zipFile   archive path
     * @param targetDir target destination directory
     * @param overwrite whether existing files should be overwritten
     * @param options   security threshold options
     * @throws IOException       if an I/O error occurs
     * @throws SecurityException if ZipSlip path traversal or ZipBomb expansion is detected
     */
    public static void decompress(Path zipFile, Path targetDir, boolean overwrite, ZipGuardOptions options) throws IOException {
        Objects.requireNonNull(zipFile, "zipFile must not be null");
        Objects.requireNonNull(targetDir, "targetDir must not be null");
        Objects.requireNonNull(options, "options must not be null");

        if (!Files.exists(zipFile)) {
            throw new NoSuchFileException("Zip file does not exist: " + zipFile);
        }

        Path canonicalTargetDir = targetDir.toAbsolutePath().normalize();
        Files.createDirectories(canonicalTargetDir);

        long zipFileSize = Files.size(zipFile);
        long totalBytesExtracted = 0;
        int entryCount = 0;
        byte[] buffer = new byte[8192];

        try (InputStream fis = Files.newInputStream(zipFile);
             BufferedInputStream bis = new BufferedInputStream(fis);
             ZipInputStream zis = new ZipInputStream(bis)) {

            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entryCount++;
                if (entryCount > options.maxEntries()) {
                    throw new SecurityException("Zip bomb detected: Exceeded max allowed entries count (" + options.maxEntries() + ")");
                }

                String entryName = entry.getName();
                Path resolvedPath = canonicalTargetDir.resolve(entryName).normalize();

                // ZipSlip path traversal guard
                if (!resolvedPath.startsWith(canonicalTargetDir)) {
                    throw new SecurityException("ZipSlip detected: Entry is outside target directory: " + entryName);
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(resolvedPath);
                    zis.closeEntry();
                    continue;
                }

                if (Files.exists(resolvedPath) && !overwrite) {
                    throw new FileAlreadyExistsException("Target file already exists: " + resolvedPath);
                }

                Path parent = resolvedPath.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }

                try (OutputStream fos = Files.newOutputStream(resolvedPath,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
                     BufferedOutputStream bos = new BufferedOutputStream(fos)) {

                    int len;
                    while ((len = zis.read(buffer)) != -1) {
                        bos.write(buffer, 0, len);
                        totalBytesExtracted += len;

                        if (totalBytesExtracted > options.maxTotalBytes()) {
                            throw new SecurityException("Zip bomb detected: Total extracted size exceeds limit of " + options.maxTotalBytes() + " bytes");
                        }
                        if (zipFileSize > 0 && options.maxExpansionRatio() > 0) {
                            long allowed = Math.max(1024L, zipFileSize * (long) options.maxExpansionRatio());
                            if (totalBytesExtracted > allowed) {
                                throw new SecurityException("Zip bomb detected: Expansion ratio exceeded max allowed (" + options.maxExpansionRatio() + "x)");
                            }
                        }
                    }
                }
                zis.closeEntry();
            }
        }
    }

    /**
     * Instance method decompression using configured options.
     */
    public void extract(Path zipFile, Path targetDir, boolean overwrite) throws IOException {
        decompress(zipFile, targetDir, overwrite, defaultOptions);
    }
}
