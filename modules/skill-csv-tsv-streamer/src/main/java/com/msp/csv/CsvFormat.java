package com.msp.csv;

import java.util.Objects;

/**
 * Configuration options for parsing and writing delimited data (CSV, TSV, custom delimiter).
 */
public record CsvFormat(
        char delimiter,
        char quoteChar,
        String lineSeparator,
        boolean hasHeader,
        boolean trimFields,
        boolean ignoreEmptyLines
) {

    public static final CsvFormat DEFAULT = new CsvFormat(',', '"', "\r\n", true, false, true);
    public static final CsvFormat TSV = new CsvFormat('\t', '"', "\n", true, false, true);
    public static final CsvFormat EXCEL = new CsvFormat(',', '"', "\r\n", true, false, true);

    public CsvFormat {
        Objects.requireNonNull(lineSeparator, "lineSeparator must not be null");
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private char delimiter = ',';
        private char quoteChar = '"';
        private String lineSeparator = "\r\n";
        private boolean hasHeader = true;
        private boolean trimFields = false;
        private boolean ignoreEmptyLines = true;

        public Builder delimiter(char delimiter) {
            this.delimiter = delimiter;
            return this;
        }

        public Builder quoteChar(char quoteChar) {
            this.quoteChar = quoteChar;
            return this;
        }

        public Builder lineSeparator(String lineSeparator) {
            this.lineSeparator = lineSeparator;
            return this;
        }

        public Builder hasHeader(boolean hasHeader) {
            this.hasHeader = hasHeader;
            return this;
        }

        public Builder trimFields(boolean trimFields) {
            this.trimFields = trimFields;
            return this;
        }

        public Builder ignoreEmptyLines(boolean ignoreEmptyLines) {
            this.ignoreEmptyLines = ignoreEmptyLines;
            return this;
        }

        public CsvFormat build() {
            return new CsvFormat(delimiter, quoteChar, lineSeparator, hasHeader, trimFields, ignoreEmptyLines);
        }
    }
}
