package com.msp.asciitable;

/**
 * Static entry point for creating formatted CLI, log, and Markdown ASCII tables.
 */
public final class AsciiTable {

    private AsciiTable() {
    }

    /**
     * Creates a new fluent {@link AsciiTableBuilder}.
     */
    public static AsciiTableBuilder builder() {
        return new AsciiTableBuilder();
    }
}
