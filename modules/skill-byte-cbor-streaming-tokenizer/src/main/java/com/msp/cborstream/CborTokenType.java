package com.msp.cborstream;

/**
 * High-level CBOR token types produced during streaming tokenization.
 */
public enum CborTokenType {
    UNSIGNED_INTEGER,
    NEGATIVE_INTEGER,
    BYTE_STRING,
    TEXT_STRING,
    START_ARRAY,
    END_ARRAY,
    START_MAP,
    END_MAP,
    TAG,
    BOOLEAN,
    NULL,
    UNDEFINED,
    FLOAT,
    SIMPLE_VALUE,
    BREAK,
    END_OF_STREAM
}
