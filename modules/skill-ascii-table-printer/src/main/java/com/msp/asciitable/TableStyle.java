package com.msp.asciitable;

/**
 * Visual styling formats for rendering tables.
 */
public enum TableStyle {
    /**
     * Standard 7-bit ASCII table with '+' and '-' borders.
     * <pre>
     * +----+-------+
     * | ID | Name  |
     * +----+-------+
     * | 1  | Alice |
     * +----+-------+
     * </pre>
     */
    ASCII,

    /**
     * Modern Unicode box-drawing table.
     * <pre>
     * ┌────┬───────┐
     * │ ID │ Name  │
     * ├────┼───────┤
     * │ 1  │ Alice │
     * └────┴───────┘
     * </pre>
     */
    UNICODE_BOX,

    /**
     * GitHub-flavored Markdown compatible table.
     * <pre>
     * | ID | Name  |
     * |:---|:------|
     * | 1  | Alice |
     * </pre>
     */
    MARKDOWN,

    /**
     * Minimal table without vertical outer borders, suitable for log output.
     * <pre>
     * ID   Name
     * ---- -------
     * 1    Alice
     * </pre>
     */
    MINIMAL
}
