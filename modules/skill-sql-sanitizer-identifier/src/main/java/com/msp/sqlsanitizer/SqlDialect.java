package com.msp.sqlsanitizer;

import java.util.Objects;

/**
 * SQL dialects supporting identifier quotation and escaping conventions.
 */
public enum SqlDialect {

    /**
     * ANSI SQL:1999 standard quoting using double quotes (").
     */
    ANSI_STANDARD('"'),

    /**
     * MySQL dialect using backticks (`).
     */
    MYSQL('`'),

    /**
     * PostgreSQL dialect using double quotes (").
     */
    POSTGRESQL('"'),

    /**
     * Oracle dialect using double quotes (").
     */
    ORACLE('"');

    private final char quoteChar;
    private final String quoteString;
    private final String escapedQuoteString;

    SqlDialect(char quoteChar) {
        this.quoteChar = quoteChar;
        this.quoteString = String.valueOf(quoteChar);
        this.escapedQuoteString = quoteString + quoteString;
    }

    public char getQuoteChar() {
        return quoteChar;
    }

    public String getQuoteString() {
        return quoteString;
    }

    /**
     * Quotes an identifier according to this dialect's rules, escaping internal quote characters.
     *
     * @param identifier the raw identifier
     * @return the safely quoted identifier
     */
    public String quote(String identifier) {
        Objects.requireNonNull(identifier, "identifier must not be null");
        String escaped = identifier.replace(quoteString, escapedQuoteString);
        return quoteChar + escaped + quoteChar;
    }
}
