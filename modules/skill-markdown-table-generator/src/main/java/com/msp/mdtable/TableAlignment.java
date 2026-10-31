package com.msp.mdtable;

/**
 * Alignment options for GitHub Flavored Markdown table columns.
 */
public enum TableAlignment {
    LEFT,
    CENTER,
    RIGHT;

    /**
     * Returns the minimum width required for this alignment delimiter.
     */
    public int getMinimumWidth() {
        return switch (this) {
            case LEFT, RIGHT -> 4;
            case CENTER -> 5;
        };
    }

    /**
     * Generates a markdown delimiter matching the specified column width.
     *
     * @param width column width (at least getMinimumWidth())
     * @return delimiter string (e.g. ":---", ":---:", "---:")
     */
    public String formatDelimiter(int width) {
        int w = Math.max(width, getMinimumWidth());
        return switch (this) {
            case LEFT -> ":" + "-".repeat(w - 1);
            case CENTER -> ":" + "-".repeat(w - 2) + ":";
            case RIGHT -> "-".repeat(w - 1) + ":";
        };
    }

    /**
     * Pads cell text according to this alignment to fit the column width.
     *
     * @param text cell content
     * @param width column width
     * @return padded cell text
     */
    public String padText(String text, int width) {
        String safeText = text == null ? "" : text;
        int pad = width - safeText.length();
        if (pad <= 0) {
            return safeText;
        }

        return switch (this) {
            case LEFT -> safeText + " ".repeat(pad);
            case RIGHT -> " ".repeat(pad) + safeText;
            case CENTER -> {
                int leftPad = pad / 2;
                int rightPad = pad - leftPad;
                yield " ".repeat(leftPad) + safeText + " ".repeat(rightPad);
            }
        };
    }
}
