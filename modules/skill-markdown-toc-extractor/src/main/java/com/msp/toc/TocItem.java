package com.msp.toc;

import java.util.Objects;

/**
 * Represents a table of contents item extracted from a markdown header.
 *
 * @param level  header level (1 for #, 2 for ##, ..., 6 for ######)
 * @param title  header title text
 * @param anchor URL slug anchor (e.g., #my-heading)
 */
public record TocItem(int level, String title, String anchor) {

    public TocItem {
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(anchor, "anchor must not be null");
    }

    /**
     * Formats this TOC item as a markdown link with indentation.
     * Indentation: 2 spaces per level beyond the minimum base level.
     */
    public String toMarkdown(int baseLevel) {
        int indentLevel = Math.max(0, level - baseLevel);
        String indent = "  ".repeat(indentLevel);
        return indent + "- [" + title + "](#" + anchor + ")";
    }
}
