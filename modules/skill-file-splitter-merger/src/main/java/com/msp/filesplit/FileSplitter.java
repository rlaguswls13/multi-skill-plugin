package com.msp.filesplit;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * High-performance file chunk splitter supporting byte-size boundaries and line-count boundaries.
 */
public final class FileSplitter {

    private static final int BUFFER_SIZE = 64 * 1024;

    private FileSplitter() {
        // Utility class
    }

    /**
     * Splits a source file into chunks of at most chunkSizeBytes.
     *
     * @param source         the source file to split
     * @param chunkSizeBytes maximum size in bytes of each chunk
     * @param outputDir      target directory to store chunk files
     * @return ordered list of paths to created chunk files
     * @throws IOException if an I/O error occurs
     */
    public static List<Path> splitBySize(Path source, long chunkSizeBytes, Path outputDir) throws IOException {
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(outputDir, "outputDir must not be null");
        if (chunkSizeBytes <= 0) {
            throw new IllegalArgumentException("chunkSizeBytes must be positive: " + chunkSizeBytes);
        }
        if (!Files.exists(source)) {
            throw new NoSuchFileException("Source file does not exist: " + source);
        }
        if (Files.isDirectory(source)) {
            throw new IllegalArgumentException("Source path is a directory: " + source);
        }

        Files.createDirectories(outputDir);
        List<Path> chunks = new ArrayList<>();
        String baseName = source.getFileName().toString();

        long fileSize = Files.size(source);
        if (fileSize == 0) {
            Path chunkPath = outputDir.resolve(String.format("%s.part%04d", baseName, 1));
            Files.createFile(chunkPath);
            chunks.add(chunkPath);
            return chunks;
        }

        try (InputStream is = Files.newInputStream(source);
             BufferedInputStream bis = new BufferedInputStream(is)) {

            int partIndex = 1;
            byte[] buffer = new byte[(int) Math.min(chunkSizeBytes, BUFFER_SIZE)];
            long currentChunkWritten = 0;
            BufferedOutputStream currentBos = null;

            try {
                int read;
                while ((read = bis.read(buffer, 0, (int) Math.min(buffer.length, chunkSizeBytes - currentChunkWritten))) != -1) {
                    if (currentBos == null) {
                        Path chunkPath = outputDir.resolve(String.format("%s.part%04d", baseName, partIndex++));
                        OutputStream os = Files.newOutputStream(chunkPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
                        currentBos = new BufferedOutputStream(os);
                        chunks.add(chunkPath);
                        currentChunkWritten = 0;
                    }

                    currentBos.write(buffer, 0, read);
                    currentChunkWritten += read;

                    if (currentChunkWritten >= chunkSizeBytes) {
                        currentBos.close();
                        currentBos = null;
                        currentChunkWritten = 0;
                    }
                }
            } finally {
                if (currentBos != null) {
                    currentBos.close();
                }
            }
        }
        return chunks;
    }

    /**
     * Splits a text file into chunks with at most linesPerChunk lines per chunk (UTF-8 encoded).
     *
     * @param source        the source text file to split
     * @param linesPerChunk maximum number of lines per chunk
     * @param outputDir     target directory to store chunk files
     * @return ordered list of paths to created chunk files
     * @throws IOException if an I/O error occurs
     */
    public static List<Path> splitByLines(Path source, long linesPerChunk, Path outputDir) throws IOException {
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(outputDir, "outputDir must not be null");
        if (linesPerChunk <= 0) {
            throw new IllegalArgumentException("linesPerChunk must be positive: " + linesPerChunk);
        }
        if (!Files.exists(source)) {
            throw new NoSuchFileException("Source file does not exist: " + source);
        }
        if (Files.isDirectory(source)) {
            throw new IllegalArgumentException("Source path is a directory: " + source);
        }

        Files.createDirectories(outputDir);
        List<Path> chunks = new ArrayList<>();
        String baseName = source.getFileName().toString();

        try (BufferedReader reader = Files.newBufferedReader(source, StandardCharsets.UTF_8)) {
            int partIndex = 1;
            long currentLines = 0;
            BufferedWriter writer = null;

            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (writer == null) {
                        Path chunkPath = outputDir.resolve(String.format("%s.part%04d", baseName, partIndex++));
                        writer = Files.newBufferedWriter(chunkPath, StandardCharsets.UTF_8,
                                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
                        chunks.add(chunkPath);
                        currentLines = 0;
                    }

                    writer.write(line);
                    writer.newLine();
                    currentLines++;

                    if (currentLines >= linesPerChunk) {
                        writer.close();
                        writer = null;
                        currentLines = 0;
                    }
                }
            } finally {
                if (writer != null) {
                    writer.close();
                }
            }
        }

        if (chunks.isEmpty() && Files.size(source) == 0) {
            Path chunkPath = outputDir.resolve(String.format("%s.part%04d", baseName, 1));
            Files.createFile(chunkPath);
            chunks.add(chunkPath);
        }

        return chunks;
    }
}
