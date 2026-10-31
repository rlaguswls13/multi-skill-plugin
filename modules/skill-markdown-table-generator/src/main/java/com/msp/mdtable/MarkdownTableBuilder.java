package com.msp.mdtable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Thread-safe / fluent builder for generating GitHub Flavored Markdown (GFM) tables.
 */
public class MarkdownTableBuilder {

    private record Column(String header, TableAlignment alignment) {}

    private final List<Column> columns = new ArrayList<>();
    private final List<List<String>> rows = new ArrayList<>();
    private boolean escapePipes = true;
    private boolean convertNewlines = true;

    public MarkdownTableBuilder() {
    }

    /**
     * Adds a column with the specified header and alignment.
     */
    public synchronized MarkdownTableBuilder addColumn(String header, TableAlignment alignment) {
        Objects.requireNonNull(header, "header must not be null");
        Objects.requireNonNull(alignment, "alignment must not be null");
        columns.add(new Column(header, alignment));
        return this;
    }

    /**
     * Adds a column with LEFT alignment by default.
     */
    public synchronized MarkdownTableBuilder addColumn(String header) {
        return addColumn(header, TableAlignment.LEFT);
    }

    /**
     * Adds a row of values. Missing values will be filled with empty strings.
     */
    public synchronized MarkdownTableBuilder addRow(Object... values) {
        if (values == null) {
            return addRow(Collections.emptyList());
        }
        return addRow(Arrays.asList(values));
    }

    /**
     * Adds a row of values from a list.
     */
    public synchronized MarkdownTableBuilder addRow(List<?> values) {
        List<String> formattedRow = new ArrayList<>();
        if (values != null) {
            for (Object val : values) {
                formattedRow.add(formatCell(val));
            }
        }
        rows.add(formattedRow);
        return this;
    }

    /**
     * Factory method creating a table builder populated from a list of Maps.
     */
    public static MarkdownTableBuilder fromMaps(
            List<? extends Map<String, ?>> mapRows,
            List<String> headers,
            Map<String, TableAlignment> alignments) {

        MarkdownTableBuilder builder = new MarkdownTableBuilder();
        if (headers == null || headers.isEmpty()) {
            return builder;
        }

        for (String header : headers) {
            TableAlignment align = (alignments != null && alignments.containsKey(header))
                    ? alignments.get(header)
                    : TableAlignment.LEFT;
            builder.addColumn(header, align);
        }

        if (mapRows != null) {
            for (Map<String, ?> rowMap : mapRows) {
                List<Object> rowValues = new ArrayList<>();
                for (String header : headers) {
                    rowValues.add(rowMap != null ? rowMap.get(header) : "");
                }
                builder.addRow(rowValues);
            }
        }

        return builder;
    }

    /**
     * Factory method creating a table builder populated from a list of Maps with default LEFT alignment.
     */
    public static MarkdownTableBuilder fromMaps(
            List<? extends Map<String, ?>> mapRows,
            List<String> headers) {
        return fromMaps(mapRows, headers, Collections.emptyMap());
    }

    public synchronized MarkdownTableBuilder setEscapePipes(boolean escapePipes) {
        this.escapePipes = escapePipes;
        return this;
    }

    public synchronized MarkdownTableBuilder setConvertNewlines(boolean convertNewlines) {
        this.convertNewlines = convertNewlines;
        return this;
    }

    public synchronized void clearRows() {
        rows.clear();
    }

    public synchronized List<String> getHeaders() {
        return columns.stream().map(Column::header).toList();
    }

    public synchronized int getRowCount() {
        return rows.size();
    }

    public synchronized int getColumnCount() {
        return columns.size();
    }

    /**
     * Generates a fully-padded, aligned GitHub Flavored Markdown table.
     */
    public synchronized String toMarkdown() {
        if (columns.isEmpty()) {
            return "";
        }

        int colCount = columns.size();
        int[] widths = new int[colCount];

        // 1. Calculate minimum widths based on headers and alignment constraints
        for (int i = 0; i < colCount; i++) {
            Column col = columns.get(i);
            int minW = Math.max(col.header().length(), col.alignment().getMinimumWidth());
            widths[i] = minW;
        }

        // 2. Expand widths based on row cell contents
        for (List<String> row : rows) {
            for (int i = 0; i < colCount; i++) {
                String cell = (i < row.size()) ? row.get(i) : "";
                if (cell.length() > widths[i]) {
                    widths[i] = cell.length();
                }
            }
        }

        StringBuilder sb = new StringBuilder();

        // 3. Build Header Row
        sb.append("|");
        for (int i = 0; i < colCount; i++) {
            Column col = columns.get(i);
            sb.append(" ").append(col.alignment().padText(col.header(), widths[i])).append(" |");
        }
        sb.append("\n");

        // 4. Build Delimiter Row
        sb.append("|");
        for (int i = 0; i < colCount; i++) {
            Column col = columns.get(i);
            sb.append(" ").append(col.alignment().formatDelimiter(widths[i])).append(" |");
        }
        sb.append("\n");

        // 5. Build Data Rows
        for (List<String> row : rows) {
            sb.append("|");
            for (int i = 0; i < colCount; i++) {
                Column col = columns.get(i);
                String cell = (i < row.size()) ? row.get(i) : "";
                sb.append(" ").append(col.alignment().padText(cell, widths[i])).append(" |");
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    @Override
    public String toString() {
        return toMarkdown();
    }

    private String formatCell(Object val) {
        if (val == null) {
            return "";
        }
        String str = String.valueOf(val);
        if (escapePipes) {
            str = str.replace("|", "\\|");
        }
        if (convertNewlines) {
            str = str.replace("\r\n", "<br>").replace("\n", "<br>").replace("\r", "<br>");
        }
        return str;
    }
}
