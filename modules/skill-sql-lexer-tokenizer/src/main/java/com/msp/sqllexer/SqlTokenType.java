package com.msp.sqllexer;

/**
 * Enumeration of SQL token types.
 */
public enum SqlTokenType {
    /**
     * SQL keywords (SELECT, FROM, WHERE, etc.).
     */
    KEYWORD,

    /**
     * Database, table, and column identifiers (regular or quoted with backticks/brackets).
     */
    IDENTIFIER,

    /**
     * String literals delimited by single quotes or double quotes.
     */
    STRING_LITERAL,

    /**
     * Integer, decimal, or scientific notation numeric literals.
     */
    NUMERIC_LITERAL,

    /**
     * Arithmetic, comparison, and concatenation operators (=, !=, <>, <, >, <=, >=, +, -, *, /, ||, etc.).
     */
    OPERATOR,

    /**
     * Punctuation symbols (parentheses, commas, semicolons, dots).
     */
    PUNCTUATION,

    /**
     * Single-line (-- or #) or multi-line (/* ... *&#47;) comments.
     */
    COMMENT
}
