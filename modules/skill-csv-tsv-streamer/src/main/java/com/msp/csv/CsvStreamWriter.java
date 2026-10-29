package com.msp.csv;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;

/**
 * Memory-efficient streaming writer for CSV/TSV format.
 * Automatically escapes delimiters, quotes, and newlines according to standard rules.
 */
public final class CsvStreamWriter implements AutoCloseable, Flushable {

    private final BufferedWriter writer;
    private final CsvFormat format;
    private List<String> currentHeaders = null;
    private boolean closed = false;

    public CsvStreamWriter(Writer writer, CsvFormat format) {
        this.writer = (writer instanceof BufferedWriter bw) ? bw : new BufferedWriter(writer, 16384);
        this.format = Objects.requireNonNull(format, "format must not be null");
    }

    public static CsvStreamWriter of(Path path, CsvFormat format, Charset charset) throws IOException {
        return new CsvStreamWriter(Files.newBufferedWriter(path, charset,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE), format);
    }

    public static CsvStreamWriter of(Path path, CsvFormat format) throws IOException {
        return of(path, format, StandardCharsets.UTF_8);
    }

    public static CsvStreamWriter of(OutputStream out, CsvFormat format) {
        return new CsvStreamWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8), format);
    }

    /**
     * Writes a header row and stores the column order for subsequent Map writes.
     */
    public synchronized CsvStreamWriter writeHeader(List<String> headers) throws IOException {
        Objects.requireNonNull(headers, "headers must not be null");
        this.currentHeaders = List.copyOf(headers);
        writeRow(headers);
        return this;
    }

    /**
     * Writes a header row and stores the column order for subsequent Map writes.
     */
    public synchronized CsvStreamWriter writeHeader(String... headers) throws IOException {
        return writeHeader(Arrays.asList(headers));
    }

    /**
     * Writes a single row of values.
     */
    public synchronized CsvStreamWriter writeRow(List<?> values) throws IOException {
        checkClosed();
        if (values == null || values.isEmpty()) {
            writer.write(format.lineSeparator());
            return this;
        }

        char delim = format.delimiter();
        char quote = format.quoteChar();
        String doubleQuote = "" + quote + quote;

        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                writer.write(delim);
            }
            Object val = values.get(i);
            if (val != null) {
                String str = String.valueOf(val);
                boolean mustQuote = str.indexOf(delim) >= 0
                        || str.indexOf(quote) >= 0
                        || str.indexOf('\n') >= 0
                        || str.indexOf('\r') >= 0;

                if (mustQuote) {
                    writer.write(quote);
                    writer.write(str.replace(String.valueOf(quote), doubleQuote));
                    writer.write(quote);
                } else {
                    writer.write(str);
                }
            }
        }
        writer.write(format.lineSeparator());
        return this;
    }

    /**
     * Writes a row using varargs.
     */
    public CsvStreamWriter writeRow(Object... values) throws IOException {
        return writeRow(values != null ? Arrays.asList(values) : Collections.emptyList());
    }

    /**
     * Writes a row from a Map using established headers.
     */
    public synchronized CsvStreamWriter writeRow(Map<String, ?> rowMap) throws IOException {
        if (currentHeaders == null) {
            throw new IllegalStateException("writeHeader(...) must be called before writing Map rows");
        }
        List<Object> values = new ArrayList<>(currentHeaders.size());
        for (String header : currentHeaders) {
            values.add(rowMap.get(header));
        }
        return writeRow(values);
    }

    /**
     * Writes multiple rows sequentially.
     */
    public CsvStreamWriter writeRows(Iterable<? extends List<?>> rows) throws IOException {
        if (rows != null) {
            for (List<?> row : rows) {
                writeRow(row);
            }
        }
        return this;
    }

    private void checkClosed() {
        if (closed) {
            throw new IllegalStateException("CsvStreamWriter is closed");
        }
    }

    @Override
    public void flush() throws IOException {
        checkClosed();
        writer.flush();
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            try {
                writer.flush();
                writer.close();
            } catch (IOException e) {
                throw new CsvException("Failed to close CSV writer", e);
            }
        }
    }
}
