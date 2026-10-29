package com.msp.csv;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Memory-efficient, high-performance streaming reader for CSV/TSV files.
 * Streams data row-by-row without loading entire files into memory.
 */
public final class CsvStreamReader implements AutoCloseable, Iterable<CsvRow> {

    private enum State {
        START_OF_FIELD,
        IN_UNQUOTED,
        IN_QUOTED,
        AFTER_QUOTE
    }

    private final BufferedReader reader;
    private final CsvFormat format;
    private final Map<String, Integer> headerMap;
    private final List<String> headers;
    private long currentRowNumber = 0;
    private boolean closed = false;

    public CsvStreamReader(Reader reader, CsvFormat format) {
        this.reader = (reader instanceof BufferedReader br) ? br : new BufferedReader(reader, 16384);
        this.format = Objects.requireNonNull(format, "format must not be null");
        this.headerMap = new LinkedHashMap<>();
        this.headers = new ArrayList<>();

        if (format.hasHeader()) {
            initHeader();
        }
    }

    public static CsvStreamReader of(Path path, CsvFormat format, Charset charset) throws IOException {
        return new CsvStreamReader(Files.newBufferedReader(path, charset), format);
    }

    public static CsvStreamReader of(Path path, CsvFormat format) throws IOException {
        return of(path, format, StandardCharsets.UTF_8);
    }

    public static CsvStreamReader of(String content, CsvFormat format) {
        return new CsvStreamReader(new StringReader(content), format);
    }

    public static CsvStreamReader of(InputStream in, CsvFormat format) {
        return new CsvStreamReader(new InputStreamReader(in, StandardCharsets.UTF_8), format);
    }

    private void initHeader() {
        try {
            List<String> rawHeaders = parseNextRow();
            if (rawHeaders != null) {
                for (int i = 0; i < rawHeaders.size(); i++) {
                    String h = rawHeaders.get(i);
                    headers.add(h);
                    headerMap.putIfAbsent(h, i);
                }
            }
        } catch (IOException e) {
            throw new CsvException("Failed to read CSV header row", e);
        }
    }

    /**
     * Returns the parsed headers (empty if header reading is disabled or file is empty).
     */
    public List<String> headers() {
        return Collections.unmodifiableList(headers);
    }

    /**
     * Reads the next {@link CsvRow}, or returns {@code null} if EOF is reached.
     */
    public CsvRow readRow() throws IOException {
        if (closed) {
            return null;
        }
        List<String> values = parseNextRow();
        if (values == null) {
            return null;
        }
        currentRowNumber++;
        return new CsvRow(currentRowNumber, values, headerMap);
    }

    /**
     * Returns a sequential {@link Stream} of {@link CsvRow}s that lazily reads from the source.
     * The stream automatically closes this reader when terminated.
     */
    public Stream<CsvRow> stream() {
        Spliterator<CsvRow> spliterator = Spliterators.spliteratorUnknownSize(
                iterator(),
                Spliterator.ORDERED | Spliterator.NONNULL
        );
        return StreamSupport.stream(spliterator, false).onClose(this::close);
    }

    @Override
    public Iterator<CsvRow> iterator() {
        return new Iterator<>() {
            private CsvRow nextRow = advance();

            private CsvRow advance() {
                try {
                    return readRow();
                } catch (IOException e) {
                    throw new CsvException("Error during CSV row streaming", e);
                }
            }

            @Override
            public boolean hasNext() {
                return nextRow != null;
            }

            @Override
            public CsvRow next() {
                if (nextRow == null) {
                    throw new NoSuchElementException("No more CSV rows");
                }
                CsvRow current = nextRow;
                nextRow = advance();
                return current;
            }
        };
    }

    private List<String> parseNextRow() throws IOException {
        List<String> rowFields = new ArrayList<>();
        StringBuilder fieldBuffer = new StringBuilder();
        State state = State.START_OF_FIELD;

        while (true) {
            int c = reader.read();

            if (c == -1) {
                if (state == State.START_OF_FIELD && rowFields.isEmpty() && fieldBuffer.isEmpty()) {
                    return null;
                }
                String val = fieldBuffer.toString();
                rowFields.add(format.trimFields() ? val.trim() : val);
                return rowFields;
            }

            char ch = (char) c;

            switch (state) {
                case START_OF_FIELD -> {
                    if (ch == format.quoteChar()) {
                        state = State.IN_QUOTED;
                    } else if (ch == format.delimiter()) {
                        rowFields.add("");
                    } else if (ch == '\r') {
                        consumeNextIf('\n');
                        if (rowFields.isEmpty() && format.ignoreEmptyLines()) {
                            continue;
                        }
                        rowFields.add("");
                        return rowFields;
                    } else if (ch == '\n') {
                        if (rowFields.isEmpty() && format.ignoreEmptyLines()) {
                            continue;
                        }
                        rowFields.add("");
                        return rowFields;
                    } else {
                        fieldBuffer.append(ch);
                        state = State.IN_UNQUOTED;
                    }
                }
                case IN_UNQUOTED -> {
                    if (ch == format.delimiter()) {
                        String val = fieldBuffer.toString();
                        rowFields.add(format.trimFields() ? val.trim() : val);
                        fieldBuffer.setLength(0);
                        state = State.START_OF_FIELD;
                    } else if (ch == '\r') {
                        consumeNextIf('\n');
                        String val = fieldBuffer.toString();
                        rowFields.add(format.trimFields() ? val.trim() : val);
                        fieldBuffer.setLength(0);
                        return rowFields;
                    } else if (ch == '\n') {
                        String val = fieldBuffer.toString();
                        rowFields.add(format.trimFields() ? val.trim() : val);
                        fieldBuffer.setLength(0);
                        return rowFields;
                    } else {
                        fieldBuffer.append(ch);
                    }
                }
                case IN_QUOTED -> {
                    if (ch == format.quoteChar()) {
                        state = State.AFTER_QUOTE;
                    } else {
                        fieldBuffer.append(ch);
                    }
                }
                case AFTER_QUOTE -> {
                    if (ch == format.quoteChar()) {
                        // Escaped quote
                        fieldBuffer.append(format.quoteChar());
                        state = State.IN_QUOTED;
                    } else if (ch == format.delimiter()) {
                        String val = fieldBuffer.toString();
                        rowFields.add(format.trimFields() ? val.trim() : val);
                        fieldBuffer.setLength(0);
                        state = State.START_OF_FIELD;
                    } else if (ch == '\r') {
                        consumeNextIf('\n');
                        String val = fieldBuffer.toString();
                        rowFields.add(format.trimFields() ? val.trim() : val);
                        fieldBuffer.setLength(0);
                        return rowFields;
                    } else if (ch == '\n') {
                        String val = fieldBuffer.toString();
                        rowFields.add(format.trimFields() ? val.trim() : val);
                        fieldBuffer.setLength(0);
                        return rowFields;
                    } else {
                        if (!format.trimFields() || !Character.isWhitespace(ch)) {
                            fieldBuffer.append(ch);
                        }
                    }
                }
            }
        }
    }

    private void consumeNextIf(char expected) throws IOException {
        reader.mark(1);
        int next = reader.read();
        if (next != expected && next != -1) {
            reader.reset();
        }
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            try {
                reader.close();
            } catch (IOException e) {
                throw new CsvException("Failed to close CSV reader", e);
            }
        }
    }
}
