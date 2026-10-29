package com.msp.csv;

import java.io.*;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * Unified static facade and factory for CSV/TSV streaming operations.
 */
public final class CsvStreamer {

    private CsvStreamer() {
    }

    /**
     * Creates a CSV reader for the specified file using default CSV format.
     */
    public static CsvStreamReader read(Path path) throws IOException {
        return CsvStreamReader.of(path, CsvFormat.DEFAULT);
    }

    /**
     * Creates a TSV reader for the specified file using TSV format.
     */
    public static CsvStreamReader readTsv(Path path) throws IOException {
        return CsvStreamReader.of(path, CsvFormat.TSV);
    }

    /**
     * Creates a reader from an in-memory CSV string.
     */
    public static CsvStreamReader readString(String csvContent) {
        return CsvStreamReader.of(csvContent, CsvFormat.DEFAULT);
    }

    /**
     * Creates a reader from an in-memory TSV string.
     */
    public static CsvStreamReader readTsvString(String tsvContent) {
        return CsvStreamReader.of(tsvContent, CsvFormat.TSV);
    }

    /**
     * Creates a CSV writer for the specified file using default CSV format.
     */
    public static CsvStreamWriter write(Path path) throws IOException {
        return CsvStreamWriter.of(path, CsvFormat.DEFAULT);
    }

    /**
     * Creates a TSV writer for the specified file using TSV format.
     */
    public static CsvStreamWriter writeTsv(Path path) throws IOException {
        return CsvStreamWriter.of(path, CsvFormat.TSV);
    }

    /**
     * Writes CSV rows into a String in memory and returns it.
     */
    public static String writeToString(Consumer<CsvStreamWriter> consumer) {
        return writeToString(consumer, CsvFormat.DEFAULT);
    }

    /**
     * Writes rows into a String in memory using the given format.
     */
    public static String writeToString(Consumer<CsvStreamWriter> consumer, CsvFormat format) {
        StringWriter sw = new StringWriter();
        try (CsvStreamWriter writer = new CsvStreamWriter(sw, format)) {
            consumer.accept(writer);
        }
        return sw.toString();
    }
}
