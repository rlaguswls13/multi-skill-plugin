package com.msp.asciitable;

import java.io.PrintStream;
import java.util.*;

/**
 * Fluent builder for creating beautifully aligned ASCII, Unicode Box, and Markdown tables.
 * Fully supports multi-line cells, East Asian wide character widths (Korean/CJK), and custom alignments.
 */
public final class AsciiTableBuilder {

    private final List<String> headers = new ArrayList<>();
    private final List<List<String>> rows = new ArrayList<>();
    private final Map<Integer, ColumnAlignment> columnAlignments = new HashMap<>();
    private TableStyle style = TableStyle.ASCII;
    private int padding = 1;
    private String nullPlaceholder = "";

    AsciiTableBuilder() {
    }

    public AsciiTableBuilder style(TableStyle style) {
        this.style = Objects.requireNonNull(style, "style must not be null");
        return this;
    }

    public AsciiTableBuilder padding(int padding) {
        if (padding < 0) {
            throw new IllegalArgumentException("padding must not be negative");
        }
        this.padding = padding;
        return this;
    }

    public AsciiTableBuilder nullPlaceholder(String placeholder) {
        this.nullPlaceholder = (placeholder != null) ? placeholder : "";
        return this;
    }

    public AsciiTableBuilder header(String... headers) {
        if (headers != null) {
            this.headers.clear();
            for (String h : headers) {
                this.headers.add(h != null ? h : "");
            }
        }
        return this;
    }

    public AsciiTableBuilder header(List<String> headers) {
        if (headers != null) {
            this.headers.clear();
            for (String h : headers) {
                this.headers.add(h != null ? h : "");
            }
        }
        return this;
    }

    public AsciiTableBuilder row(Object... cells) {
        if (cells != null) {
            List<String> rowList = new ArrayList<>(cells.length);
            for (Object c : cells) {
                rowList.add(c != null ? c.toString() : this.nullPlaceholder);
            }
            this.rows.add(rowList);
        }
        return this;
    }

    public AsciiTableBuilder row(List<?> cells) {
        if (cells != null) {
            List<String> rowList = new ArrayList<>(cells.size());
            for (Object c : cells) {
                rowList.add(c != null ? c.toString() : this.nullPlaceholder);
            }
            this.rows.add(rowList);
        }
        return this;
    }

    public AsciiTableBuilder alignment(int columnIndex, ColumnAlignment alignment) {
        if (columnIndex < 0) {
            throw new IllegalArgumentException("columnIndex must not be negative");
        }
        this.columnAlignments.put(columnIndex, Objects.requireNonNull(alignment, "alignment must not be null"));
        return this;
    }

    public AsciiTableBuilder alignments(ColumnAlignment... alignments) {
        if (alignments != null) {
            for (int i = 0; i < alignments.length; i++) {
                if (alignments[i] != null) {
                    this.columnAlignments.put(i, alignments[i]);
                }
            }
        }
        return this;
    }

    /**
     * Renders the table to a string.
     */
    public String render() {
        int colCount = calculateColumnCount();
        if (colCount == 0) {
            return "";
        }

        int[] colWidths = calculateColumnWidths(colCount);
        StringBuilder sb = new StringBuilder();

        switch (this.style) {
            case ASCII -> renderAsciiTable(sb, colCount, colWidths);
            case UNICODE_BOX -> renderUnicodeBoxTable(sb, colCount, colWidths);
            case MARKDOWN -> renderMarkdownTable(sb, colCount, colWidths);
            case MINIMAL -> renderMinimalTable(sb, colCount, colWidths);
        }

        return sb.toString();
    }

    /**
     * Prints the rendered table to System.out and returns the string.
     */
    public String print() {
        return print(System.out);
    }

    /**
     * Prints the rendered table to the specified PrintStream and returns the string.
     */
    public String print(PrintStream out) {
        String rendered = render();
        if (out != null) {
            out.print(rendered);
        }
        return rendered;
    }

    @Override
    public String toString() {
        return render();
    }

    // --- Rendering Helpers ---

    private int calculateColumnCount() {
        int count = this.headers.size();
        for (List<String> row : this.rows) {
            count = Math.max(count, row.size());
        }
        return count;
    }

    private int[] calculateColumnWidths(int colCount) {
        int[] widths = new int[colCount];
        int minWidth = (this.style == TableStyle.MARKDOWN) ? 3 : 1;
        Arrays.fill(widths, minWidth);

        // Header widths
        for (int c = 0; c < this.headers.size(); c++) {
            for (String line : splitLines(this.headers.get(c))) {
                widths[c] = Math.max(widths[c], displayWidth(line));
            }
        }

        // Row cell widths
        for (List<String> row : this.rows) {
            for (int c = 0; c < row.size(); c++) {
                for (String line : splitLines(row.get(c))) {
                    widths[c] = Math.max(widths[c], displayWidth(line));
                }
            }
        }

        return widths;
    }

    private void renderAsciiTable(StringBuilder sb, int colCount, int[] colWidths) {
        String sep = buildBorderLine(colCount, colWidths, '+', '-', '+', '+');

        sb.append(sep).append("\n");
        if (!this.headers.isEmpty()) {
            renderRow(sb, colCount, colWidths, this.headers, '|', '|', '|');
            sb.append(sep).append("\n");
        }
        for (List<String> row : this.rows) {
            renderRow(sb, colCount, colWidths, row, '|', '|', '|');
        }
        sb.append(sep).append("\n");
    }

    private void renderUnicodeBoxTable(StringBuilder sb, int colCount, int[] colWidths) {
        String top = buildBorderLine(colCount, colWidths, '┌', '─', '┬', '┐');
        String mid = buildBorderLine(colCount, colWidths, '├', '─', '┼', '┤');
        String bot = buildBorderLine(colCount, colWidths, '└', '─', '┴', '┘');

        sb.append(top).append("\n");
        if (!this.headers.isEmpty()) {
            renderRow(sb, colCount, colWidths, this.headers, '│', '│', '│');
            sb.append(mid).append("\n");
        }
        for (List<String> row : this.rows) {
            renderRow(sb, colCount, colWidths, row, '│', '│', '│');
        }
        sb.append(bot).append("\n");
    }

    private void renderMarkdownTable(StringBuilder sb, int colCount, int[] colWidths) {
        // Markdown header
        List<String> hdr = this.headers.isEmpty() ? Collections.nCopies(colCount, "") : this.headers;
        renderRow(sb, colCount, colWidths, hdr, '|', '|', '|');

        // Markdown separator line with alignment indicators
        sb.append('|');
        for (int c = 0; c < colCount; c++) {
            ColumnAlignment align = getAlignment(c);
            int width = colWidths[c] + (padding * 2);
            String dashes;
            switch (align) {
                case LEFT -> dashes = ":" + "-".repeat(Math.max(1, width - 1));
                case RIGHT -> dashes = "-".repeat(Math.max(1, width - 1)) + ":";
                case CENTER -> dashes = ":" + "-".repeat(Math.max(1, width - 2)) + ":";
                default -> dashes = "-".repeat(width);
            }
            sb.append(dashes).append('|');
        }
        sb.append("\n");

        for (List<String> row : this.rows) {
            renderRow(sb, colCount, colWidths, row, '|', '|', '|');
        }
    }

    private void renderMinimalTable(StringBuilder sb, int colCount, int[] colWidths) {
        String pad = " ".repeat(this.padding);
        if (!this.headers.isEmpty()) {
            renderRow(sb, colCount, colWidths, this.headers, '\0', ' ', '\0');
            // underline
            for (int c = 0; c < colCount; c++) {
                if (c > 0) sb.append("  ");
                sb.append(pad).append("-".repeat(colWidths[c])).append(pad);
            }
            sb.append("\n");
        }
        for (List<String> row : this.rows) {
            renderRow(sb, colCount, colWidths, row, '\0', ' ', '\0');
        }
    }

    private void renderRow(StringBuilder sb, int colCount, int[] colWidths, List<String> cells,
                           char leftBorder, char midBorder, char rightBorder) {
        List<List<String>> cellLines = new ArrayList<>(colCount);
        int maxLines = 1;

        for (int c = 0; c < colCount; c++) {
            String content = (c < cells.size()) ? cells.get(c) : "";
            List<String> lines = splitLines(content);
            cellLines.add(lines);
            maxLines = Math.max(maxLines, lines.size());
        }

        String padStr = " ".repeat(this.padding);

        for (int lineIdx = 0; lineIdx < maxLines; lineIdx++) {
            if (leftBorder != '\0') {
                sb.append(leftBorder);
            }

            for (int c = 0; c < colCount; c++) {
                if (c > 0 && midBorder != '\0') {
                    sb.append(midBorder);
                } else if (c > 0 && midBorder == '\0') {
                    sb.append("  ");
                }

                List<String> lines = cellLines.get(c);
                String lineText = (lineIdx < lines.size()) ? lines.get(lineIdx) : "";
                ColumnAlignment align = getAlignment(c);

                sb.append(padStr);
                appendAligned(sb, lineText, colWidths[c], align);
                sb.append(padStr);
            }

            if (rightBorder != '\0') {
                sb.append(rightBorder);
            }
            sb.append("\n");
        }
    }

    private String buildBorderLine(int colCount, int[] colWidths, char left, char horiz, char mid, char right) {
        StringBuilder b = new StringBuilder();
        b.append(left);
        for (int c = 0; c < colCount; c++) {
            if (c > 0) b.append(mid);
            int totalLen = colWidths[c] + (this.padding * 2);
            b.append(String.valueOf(horiz).repeat(totalLen));
        }
        b.append(right);
        return b.toString();
    }

    private void appendAligned(StringBuilder sb, String text, int targetWidth, ColumnAlignment alignment) {
        int dWidth = displayWidth(text);
        int spacesNeeded = Math.max(0, targetWidth - dWidth);

        switch (alignment) {
            case LEFT -> {
                sb.append(text);
                sb.append(" ".repeat(spacesNeeded));
            }
            case RIGHT -> {
                sb.append(" ".repeat(spacesNeeded));
                sb.append(text);
            }
            case CENTER -> {
                int left = spacesNeeded / 2;
                int right = spacesNeeded - left;
                sb.append(" ".repeat(left));
                sb.append(text);
                sb.append(" ".repeat(right));
            }
        }
    }

    private ColumnAlignment getAlignment(int colIndex) {
        return this.columnAlignments.getOrDefault(colIndex, ColumnAlignment.LEFT);
    }

    private static List<String> splitLines(String text) {
        if (text == null || text.isEmpty()) {
            return List.of("");
        }
        return Arrays.asList(text.split("\\R", -1));
    }

    /**
     * Calculates the visual display width of a string in monospace terminals.
     * CJK / Korean characters occupy 2 visual cells; ASCII characters occupy 1 visual cell.
     */
    public static int displayWidth(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int width = 0;
        int len = text.length();
        for (int i = 0; i < len; i++) {
            char c = text.charAt(i);
            width += charDisplayWidth(c);
        }
        return width;
    }

    public static int charDisplayWidth(char c) {
        if ((c >= 0xAC00 && c <= 0xD7A3) // Hangul syllables
                || (c >= 0x1100 && c <= 0x11FF) // Hangul jamo
                || (c >= 0x3130 && c <= 0x318F) // Hangul compatibility jamo
                || (c >= 0x4E00 && c <= 0x9FFF) // CJK unified ideographs
                || (c >= 0xFF01 && c <= 0xFF60)) { // Fullwidth ASCII
            return 2;
        }
        return 1;
    }
}
