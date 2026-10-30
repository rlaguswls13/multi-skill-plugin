package com.msp.csvsqlite;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Pure Java 21 CSV to SQLite DDL and batch INSERT dump streaming converter.
 * Automatically infers SQLite data types (INTEGER, REAL, TEXT) from sample rows.
 */
public class CsvSqliteConverter {

    private static final int DEFAULT_SAMPLE_LIMIT = 500;
    private static final int DEFAULT_BATCH_SIZE = 100;
    private static final Pattern INTEGER_PATTERN = Pattern.compile("^-?\\d+$");
    private static final Pattern REAL_PATTERN = Pattern.compile("^-?\\d*\\.?\\d+([eE][+-]?\\d+)?$");

    public enum SqliteType {
        INTEGER,
        REAL,
        TEXT
    }

    /**
     * Converts a CSV input stream to a complete SQLite SQL script string.
     *
     * @param csvStream the input stream of the CSV file
     * @param tableName target SQLite table name
     * @return full SQL script string containing DDL and batch INSERT statements
     * @throws IOException if reading the stream fails
     */
    public String generateSqlScript(InputStream csvStream, String tableName) throws IOException {
        StringWriter writer = new StringWriter();
        convert(csvStream, tableName, DEFAULT_BATCH_SIZE, stmt -> {
            writer.write(stmt);
            writer.write("\n");
        });
        return writer.toString();
    }

    /**
     * Converts a CSV input stream in a streaming fashion, emitting each SQL statement to the consumer.
     *
     * @param csvStream   the input stream of the CSV file
     * @param tableName   target SQLite table name
     * @param sqlConsumer callback accepting each generated SQL statement
     * @throws IOException if reading the stream fails
     */
    public void convert(InputStream csvStream, String tableName, Consumer<String> sqlConsumer) throws IOException {
        convert(csvStream, tableName, DEFAULT_BATCH_SIZE, sqlConsumer);
    }

    /**
     * Converts a CSV input stream with a custom batch size for INSERT statements.
     *
     * @param csvStream   the input stream of the CSV file
     * @param tableName   target SQLite table name
     * @param batchSize   number of rows per INSERT statement
     * @param sqlConsumer callback accepting each generated SQL statement
     * @throws IOException if reading the stream fails
     */
    public void convert(InputStream csvStream, String tableName, int batchSize, Consumer<String> sqlConsumer) throws IOException {
        Objects.requireNonNull(csvStream, "csvStream must not be null");
        Objects.requireNonNull(tableName, "tableName must not be null");
        Objects.requireNonNull(sqlConsumer, "sqlConsumer must not be null");

        BufferedReader reader = new BufferedReader(new InputStreamReader(csvStream, StandardCharsets.UTF_8));
        convert(reader, tableName, batchSize, sqlConsumer);
    }

    /**
     * Converts from a Reader in a streaming fashion.
     *
     * @param reader      the CSV character reader
     * @param tableName   target SQLite table name
     * @param batchSize   number of rows per INSERT statement
     * @param sqlConsumer callback accepting each generated SQL statement
     * @throws IOException if reading fails
     */
    public void convert(Reader reader, String tableName, int batchSize, Consumer<String> sqlConsumer) throws IOException {
        CsvParser parser = new CsvParser(reader);
        List<String> headers = parser.readNextRow();
        if (headers == null || headers.isEmpty()) {
            return;
        }

        // Sanitize headers
        List<String> colNames = new ArrayList<>();
        for (int i = 0; i < headers.size(); i++) {
            String col = headers.get(i).trim();
            if (col.isEmpty()) {
                col = "column_" + (i + 1);
            }
            colNames.add(col);
        }

        // Buffer sample rows to infer column types
        List<List<String>> sampleRows = new ArrayList<>();
        for (int i = 0; i < DEFAULT_SAMPLE_LIMIT; i++) {
            List<String> row = parser.readNextRow();
            if (row == null) {
                break;
            }
            sampleRows.add(row);
        }

        // Infer column data types
        SqliteType[] types = inferColumnTypes(colNames.size(), sampleRows);

        // Emit DDL
        StringBuilder ddl = new StringBuilder();
        ddl.append("CREATE TABLE IF NOT EXISTS ").append(quoteIdentifier(tableName)).append(" (");
        for (int i = 0; i < colNames.size(); i++) {
            if (i > 0) ddl.append(", ");
            ddl.append(quoteIdentifier(colNames.get(i))).append(" ").append(types[i].name());
        }
        ddl.append(");");
        sqlConsumer.accept(ddl.toString());

        // Emit BEGIN TRANSACTION
        sqlConsumer.accept("BEGIN TRANSACTION;");

        // Emit inserts for sample rows
        List<List<String>> currentBatch = new ArrayList<>();
        for (List<String> row : sampleRows) {
            currentBatch.add(row);
            if (currentBatch.size() >= batchSize) {
                sqlConsumer.accept(buildBatchInsert(tableName, colNames, types, currentBatch));
                currentBatch.clear();
            }
        }

        // Stream remaining rows from parser
        List<String> row;
        while ((row = parser.readNextRow()) != null) {
            currentBatch.add(row);
            if (currentBatch.size() >= batchSize) {
                sqlConsumer.accept(buildBatchInsert(tableName, colNames, types, currentBatch));
                currentBatch.clear();
            }
        }

        if (!currentBatch.isEmpty()) {
            sqlConsumer.accept(buildBatchInsert(tableName, colNames, types, currentBatch));
            currentBatch.clear();
        }

        // Emit COMMIT
        sqlConsumer.accept("COMMIT;");
    }

    private SqliteType[] inferColumnTypes(int colCount, List<List<String>> sampleRows) {
        SqliteType[] types = new SqliteType[colCount];
        boolean[] hasValues = new boolean[colCount];
        boolean[] allInteger = new boolean[colCount];
        boolean[] allReal = new boolean[colCount];

        Arrays.fill(allInteger, true);
        Arrays.fill(allReal, true);

        for (List<String> row : sampleRows) {
            for (int i = 0; i < colCount; i++) {
                String val = (i < row.size()) ? row.get(i).trim() : "";
                if (!val.isEmpty()) {
                    hasValues[i] = true;
                    if (allInteger[i]) {
                        if (!isInteger(val)) {
                            allInteger[i] = false;
                        }
                    }
                    if (allReal[i]) {
                        if (!isReal(val)) {
                            allReal[i] = false;
                        }
                    }
                }
            }
        }

        for (int i = 0; i < colCount; i++) {
            if (!hasValues[i]) {
                types[i] = SqliteType.TEXT;
            } else if (allInteger[i]) {
                types[i] = SqliteType.INTEGER;
            } else if (allReal[i]) {
                types[i] = SqliteType.REAL;
            } else {
                types[i] = SqliteType.TEXT;
            }
        }

        return types;
    }

    private boolean isInteger(String val) {
        if (!INTEGER_PATTERN.matcher(val).matches()) {
            return false;
        }
        try {
            Long.parseLong(val);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean isReal(String val) {
        if (!REAL_PATTERN.matcher(val).matches()) {
            return false;
        }
        try {
            double d = Double.parseDouble(val);
            return !Double.isNaN(d) && !Double.isInfinite(d);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private String buildBatchInsert(String tableName, List<String> colNames, SqliteType[] types, List<List<String>> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append("INSERT INTO ").append(quoteIdentifier(tableName)).append(" (");
        for (int i = 0; i < colNames.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(quoteIdentifier(colNames.get(i)));
        }
        sb.append(") VALUES\n");

        for (int r = 0; r < rows.size(); r++) {
            List<String> row = rows.get(r);
            sb.append("  (");
            for (int c = 0; c < colNames.size(); c++) {
                if (c > 0) sb.append(", ");
                String val = (c < row.size()) ? row.get(c) : null;
                sb.append(formatSqlValue(val, types[c]));
            }
            sb.append(")");
            if (r < rows.size() - 1) {
                sb.append(",\n");
            } else {
                sb.append(";");
            }
        }
        return sb.toString();
    }

    private String formatSqlValue(String val, SqliteType type) {
        if (val == null || (val.isEmpty() && type != SqliteType.TEXT)) {
            return "NULL";
        }
        return switch (type) {
            case INTEGER -> {
                String trimmed = val.trim();
                yield isInteger(trimmed) ? trimmed : quoteString(val);
            }
            case REAL -> {
                String trimmed = val.trim();
                yield isReal(trimmed) ? trimmed : quoteString(val);
            }
            case TEXT -> quoteString(val);
        };
    }

    private String quoteString(String str) {
        return "'" + str.replace("'", "''") + "'";
    }

    private String quoteIdentifier(String id) {
        return "[" + id.replace("]", "]]") + "]";
    }

    /**
     * RFC-4180 compliant lightweight streaming CSV Parser.
     */
    public static class CsvParser {
        private final Reader reader;
        private boolean eof = false;

        public CsvParser(Reader reader) {
            this.reader = reader.markSupported() ? reader : new BufferedReader(reader);
        }

        public List<String> readNextRow() throws IOException {
            if (eof) {
                return null;
            }

            List<String> row = new ArrayList<>();
            StringBuilder current = new StringBuilder();
            boolean inQuotes = false;
            boolean hasData = false;

            while (true) {
                int ch = reader.read();
                if (ch == -1) {
                    eof = true;
                    if (hasData || !row.isEmpty()) {
                        row.add(current.toString());
                        return row;
                    }
                    return null;
                }

                char c = (char) ch;
                if (inQuotes) {
                    if (c == '"') {
                        reader.mark(1);
                        int next = reader.read();
                        if (next == '"') {
                            current.append('"');
                        } else {
                            if (next != -1) {
                                reader.reset();
                            }
                            inQuotes = false;
                        }
                    } else {
                        current.append(c);
                    }
                } else {
                    if (c == '"') {
                        inQuotes = true;
                        hasData = true;
                    } else if (c == ',') {
                        row.add(current.toString());
                        current.setLength(0);
                        hasData = true;
                    } else if (c == '\r') {
                        reader.mark(1);
                        int next = reader.read();
                        if (next != '\n' && next != -1) {
                            reader.reset();
                        }
                        row.add(current.toString());
                        return row;
                    } else if (c == '\n') {
                        row.add(current.toString());
                        return row;
                    } else {
                        current.append(c);
                        hasData = true;
                    }
                }
            }
        }
    }
}
