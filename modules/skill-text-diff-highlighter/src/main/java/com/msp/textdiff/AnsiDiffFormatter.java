package com.msp.textdiff;

import java.util.List;

/**
 * Renders diff output for terminal/console with ANSI 16-color escape sequences.
 * Groups consecutive token changes into contiguous color spans.
 */
public final class AnsiDiffFormatter {

    public static final String RESET = "\u001B[0m";
    public static final String RED = "\u001B[31m";
    public static final String GREEN = "\u001B[32m";
    public static final String GRAY = "\u001B[90m";

    private AnsiDiffFormatter() {
        // Utility class
    }

    /**
     * Formats line differences into unified console output.
     *
     * @param lineDiffs List of line diffs.
     * @return ANSI colored string with line indicators (+, -, space).
     */
    public static String formatLines(List<LineDiff> lineDiffs) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lineDiffs.size(); i++) {
            LineDiff diff = lineDiffs.get(i);
            if (i > 0) {
                sb.append(System.lineSeparator());
            }
            switch (diff.type()) {
                case EQUAL -> sb.append("  ").append(diff.line());
                case DELETE -> sb.append(RED).append("- ").append(diff.line()).append(RESET);
                case INSERT -> sb.append(GREEN).append("+ ").append(diff.line()).append(RESET);
            }
        }
        return sb.toString();
    }

    /**
     * Formats word/token differences inline with brackets and ANSI colors,
     * cleanly grouping adjacent tokens of the same type.
     *
     * @param wordDiffs List of word diffs.
     * @return ANSI colored inline string.
     */
    public static String formatWords(List<WordDiff> wordDiffs) {
        StringBuilder sb = new StringBuilder();
        DiffType currentType = null;
        StringBuilder currentBuffer = new StringBuilder();

        for (WordDiff diff : wordDiffs) {
            if (currentType == null) {
                currentType = diff.type();
                currentBuffer.append(diff.text());
            } else if (diff.type() == currentType) {
                currentBuffer.append(diff.text());
            } else {
                appendAnsiChunk(sb, currentType, currentBuffer.toString());
                currentType = diff.type();
                currentBuffer.setLength(0);
                currentBuffer.append(diff.text());
            }
        }
        if (currentType != null && !currentBuffer.isEmpty()) {
            appendAnsiChunk(sb, currentType, currentBuffer.toString());
        }
        return sb.toString();
    }

    private static void appendAnsiChunk(StringBuilder sb, DiffType type, String text) {
        switch (type) {
            case EQUAL -> sb.append(text);
            case DELETE -> sb.append(RED).append("[-").append(text).append("-]").append(RESET);
            case INSERT -> sb.append(GREEN).append("[+").append(text).append("+]").append(RESET);
        }
    }
}
